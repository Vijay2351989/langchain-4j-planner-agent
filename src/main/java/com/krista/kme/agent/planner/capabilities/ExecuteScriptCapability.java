package com.krista.kme.agent.planner.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krista.kme.agent.planner.ScriptCorrectionAgent;
import com.krista.kme.agent.planner.ScriptCorrectionResponse;

import dev.langchain4j.model.chat.ChatLanguageModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;

/**
 * Capability for executing custom scripts when no other capability matches the task.
 *
 * This capability allows the LLM to generate and EXECUTE scripts (bash, Python, JavaScript, etc.)
 * for tasks that don't map to existing capabilities but the LLM knows how to solve.
 *
 * The LLM should provide:
 * 1. Script type (bash, python, javascript, node, etc.)
 * 2. The actual script code
 * 3. Brief explanation of what the script does
 *
 * Input format (JSON):
 * {
 *   "scriptType": "bash|python|python3|javascript|node|sh|etc",
 *   "script": "actual script code here",
 *   "description": "what this script does"
 * }
 *
 * Or simple text format:
 * ```bash
 * echo "Hello World"
 * ```
 *
 * Output: The script is EXECUTED and the actual output is returned.
 *
 * Supported script types:
 * - bash, sh: Bash shell scripts
 * - python, python3: Python scripts
 * - javascript, node, js: Node.js scripts
 * - ruby: Ruby scripts
 * - perl: Perl scripts
 *
 * Security: Scripts are executed with a timeout (30 seconds) to prevent infinite loops.
 */
public class ExecuteScriptCapability extends Capability {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteScriptCapability.class);

    // Execution timeout in seconds
    private static final int EXECUTION_TIMEOUT_SECONDS = 30;

    // Maximum retry attempts for self-correction
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // JSON parser for handling script input
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Script correction agent for self-healing
    private final ScriptCorrectionAgent correctionAgent;

    public ExecuteScriptCapability(ChatLanguageModel model) {
        super(
            6,
            "ExecuteScript",
            "Generates and EXECUTES scripts for tasks requiring file operations, system commands, or data processing. " +
            "Use this for: file system operations, shell commands, parsing files, data transformation, JSON manipulation, etc. " +
            "DO NOT use for simple questions you can answer directly. " +
            "Scripts are executed with a 30-second timeout. " +
            "SELF-CORRECTING: If a script fails, it will automatically retry up to 3 times with AI-powered error correction. " +
            "Supported script types: bash, sh, python, python3, javascript, node, js, ruby, perl.",

            // Input schema
            "{\n" +
            "  \"scriptType\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The type of script to execute. Options: bash, sh, python, python3, javascript, node, js, ruby, perl.\"\n" +
            "  },\n" +
            "  \"script\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The actual script code to execute. IMPORTANT: When using JSON format, properly escape special characters: use \\\\n for newlines, \\\\t for tabs, \\\\\\\" for quotes, \\\\\\\\ for backslashes. The script MUST print/output the final result. If you define a function, you MUST call it and print the result.\"\n" +
            "  },\n" +
            "  \"description\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": false,\n" +
            "    \"description\": \"Optional brief explanation of what the script does.\"\n" +
            "  }\n" +
            "}\n" +
            "Alternative: You can also use markdown code block format: ```bash\\necho 'test'\\n```"
        );
        this.correctionAgent = new ScriptCorrectionAgent(model);
    }
    
    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing ExecuteScript capability with self-correction");

        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Script input is required");
        }

        try {
            ScriptInfo scriptInfo = parseScriptInput(input);

            logger.info("Executing script: type={}, length={} chars, description={}",
                scriptInfo.scriptType, scriptInfo.script.length(), scriptInfo.description);

            // Execute with retry and self-correction
            return executeWithRetry(scriptInfo);

        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to execute script", e);
            throw new CapabilityExecutionException("Failed to execute script: " + e.getMessage(), e);
        }
    }

    /**
     * Execute script with automatic retry and LLM-based error correction
     */
    private CapabilityResult executeWithRetry(ScriptInfo scriptInfo) throws CapabilityExecutionException {
        ScriptInfo currentScript = scriptInfo;
        String lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            logger.info("Script execution attempt {}/{}", attempt, MAX_RETRY_ATTEMPTS);

            try {
                // Execute the script
                ScriptExecutionResult result = executeScript(currentScript);
                logger.info("Script execution result: {}", result);

                if (result.exitCode == 0) {
                    // Check if output is empty or just whitespace
                    String trimmedOutput = result.output.trim();

                    if (trimmedOutput.isEmpty() || trimmedOutput.equalsIgnoreCase("none")) {
                        // Treat empty/None output as an error
                        lastError = "Script executed successfully but produced no output (or returned None). " +
                                   "The script must print/output the final result. " +
                                   "If you defined a function, you need to call it and print the result.";

                        logger.warn("Script produced empty output on attempt {}", attempt);

                        // If not the last attempt, try to fix the script
                        if (attempt < MAX_RETRY_ATTEMPTS) {
                            logger.info("Attempting to fix script to produce output...");
                            currentScript = fixScriptWithLLM(currentScript, lastError);
                            logger.info("Script corrected to produce output, retrying...");
                            continue; // Skip to next iteration
                        } else {
                            // Last attempt - return error
                            throw new CapabilityExecutionException(
                                "Script executed successfully but produced no output after " +
                                MAX_RETRY_ATTEMPTS + " attempts. " +
                                "The script must print/output the final result."
                            );
                        }
                    }

                    String message = String.format(
                        "Script executed successfully (%s) on attempt %d/%d:\n%s",
                        currentScript.scriptType,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        currentScript.description
                    );

                    if (attempt > 1) {
                        message += "\n\n✅ Self-corrected after " + (attempt - 1) + " failed attempt(s)";
                    }

                    logger.info("Script executed successfully on attempt {}, output length: {} chars",
                        attempt, result.output.length());
                    return CapabilityResult.success(result.output, message);
                } else {
                    // Script failed - prepare error message
                    lastError = String.format(
                        "Exit code %d\nSTDOUT:\n%s\nSTDERR:\n%s",
                        result.exitCode,
                        result.output,
                        result.error
                    );

                    logger.warn("Script execution failed on attempt {}: {}", attempt, lastError);

                    // If not the last attempt, try to fix the script
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        logger.info("Attempting to fix script using LLM...");
                        currentScript = fixScriptWithLLM(currentScript, lastError);
                        logger.info("Script corrected, retrying...");
                    }
                }

            } catch (Exception e) {
                lastError = e.getMessage();
                logger.warn("Script execution threw exception on attempt {}: {}", attempt, lastError);

                // If not the last attempt, try to fix the script
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        logger.info("Attempting to fix script using LLM...");
                        currentScript = fixScriptWithLLM(currentScript, lastError);
                        logger.info("Script corrected, retrying...");
                    } catch (Exception fixError) {
                        logger.error("Failed to fix script: {}", fixError.getMessage());
                        // Continue to next attempt or fail
                    }
                }
            }
        }

        // All attempts failed
        String errorMessage = String.format(
            "Script execution failed after %d attempts.\n\nLast error:\n%s\n\nLast script:\n%s",
            MAX_RETRY_ATTEMPTS,
            lastError,
            currentScript.script
        );

        logger.error("Script execution failed after {} attempts", MAX_RETRY_ATTEMPTS);
        throw new CapabilityExecutionException(errorMessage);
    }

    /**
     * Use ScriptCorrectionAgent to fix a failing script based on error message
     */
    private ScriptInfo fixScriptWithLLM(ScriptInfo failedScript, String errorMessage) throws CapabilityExecutionException {
        logger.debug("Requesting script correction from ScriptCorrectionAgent");

        try {
            // Use the dedicated ScriptCorrectionAgent
            ScriptCorrectionResponse response = correctionAgent.correctScript(
                failedScript.scriptType,
                failedScript.script,
                errorMessage,
                failedScript.description
            );

            // Check if fix was applied
            if (!response.isFixApplied()) {
                throw new CapabilityExecutionException(
                    "ScriptCorrectionAgent could not fix the script: " + response.getDescription()
                );
            }

            // Create new ScriptInfo with corrected script
            ScriptInfo fixedScript = new ScriptInfo(
                response.getScriptType(),
                response.getScript(),
                "Fixed: " + response.getDescription()
            );

            logger.info("Script corrected by ScriptCorrectionAgent: {}", response.getDescription());
            return fixedScript;

        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to get script correction: {}", e.getMessage());
            throw new CapabilityExecutionException("Failed to correct script: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse the input to extract script information
     */
    private ScriptInfo parseScriptInput(String input) throws CapabilityExecutionException {
        String trimmedInput = input.trim();

        // Check if input is JSON object format
        if (trimmedInput.startsWith("{")) {
            return parseJsonFormat(trimmedInput);
        }

        // Check if input is JSON array format (for batch processing or data input)
        if (trimmedInput.startsWith("[")) {
            return parseJsonArrayFormat(trimmedInput);
        }

        // Check if input is markdown code block format
        if (trimmedInput.contains("```")) {
            return parseMarkdownFormat(trimmedInput);
        }

        // Treat as plain script (assume bash)
        return new ScriptInfo("bash", trimmedInput, "Custom script");
    }
    
    /**
     * Parse JSON format input using Jackson for proper escape handling
     */
    private ScriptInfo parseJsonFormat(String json) throws CapabilityExecutionException {
        // Log the original JSON for debugging
        logger.debug("Original JSON input (length={}): {}", json.length(),
                    json.length() <= 500 ? json : json.substring(0, 500) + "...");

        // First, try to automatically fix common JSON issues (unescaped newlines, tabs, etc.)
        String fixedJson = fixMalformedJson(json);

        if (!fixedJson.equals(json)) {
            logger.info("JSON was auto-fixed. Fixed JSON (length={}): {}", fixedJson.length(),
                       fixedJson.length() <= 500 ? fixedJson : fixedJson.substring(0, 500) + "...");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(fixedJson);

            String scriptType = rootNode.has("scriptType")
                ? rootNode.get("scriptType").asText("bash")
                : "bash";

            String script = rootNode.has("script")
                ? rootNode.get("script").asText()
                : null;

            String description = rootNode.has("description")
                ? rootNode.get("description").asText("Custom script")
                : "Custom script";

            if (script == null || script.isEmpty()) {
                throw new CapabilityExecutionException("Script code is required in JSON input");
            }

            logger.debug("Parsed JSON: scriptType={}, script length={}, description={}",
                scriptType, script.length(), description);

            return new ScriptInfo(scriptType, script, description);

        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            // Log the problematic JSON to help diagnose the issue
            System.err.println("❌ JSON PARSE ERROR DETECTED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("Location: line=" + (e.getLocation() != null ? e.getLocation().getLineNr() : "?") +
                             ", column=" + (e.getLocation() != null ? e.getLocation().getColumnNr() : "?"));

            logger.error("❌ JSON PARSE ERROR at line {}, column {}",
                        e.getLocation() != null ? e.getLocation().getLineNr() : "?",
                        e.getLocation() != null ? e.getLocation().getColumnNr() : "?");
            logger.error("Error message: {}", e.getMessage());

            // Log the FIXED JSON (what we actually tried to parse)
            if (fixedJson.length() <= 2000) {
                logger.error("Fixed JSON that failed to parse:\n{}",
                           fixedJson.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
                System.err.println("Fixed JSON: " + fixedJson.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
            } else {
                logger.error("Fixed JSON (first 2000 chars):\n{}",
                           fixedJson.substring(0, 2000).replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
                System.err.println("Fixed JSON (first 2000 chars): " +
                                 fixedJson.substring(0, 2000).replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
            }

            // Show context around the error in the FIXED JSON
            if (e.getLocation() != null && e.getLocation().getColumnNr() > 0) {
                int errorPos = (int) e.getLocation().getColumnNr() - 1;
                int start = Math.max(0, errorPos - 80);
                int end = Math.min(fixedJson.length(), errorPos + 80);

                String context = fixedJson.substring(start, end);
                logger.error("Context around error position {} in FIXED JSON:", errorPos);
                logger.error("  '{}'", context.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
                logger.error("  {}^ ERROR HERE", " ".repeat(Math.min(80, errorPos - start)));

                System.err.println("\nContext around position " + errorPos + " in FIXED JSON:");
                System.err.println("  '" + context.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "'");
                System.err.println("  " + " ".repeat(Math.min(80, errorPos - start)) + "^ ERROR HERE");
            }

            // Note: We already tried to fix the JSON before parsing (see fixMalformedJson call at start of method)
            // If we still got an error, it means the JSON has issues we can't automatically fix
            logger.error("❌ Failed to parse JSON even after automatic fixes");
            throw new CapabilityExecutionException(
                "Failed to parse JSON input: " + e.getMessage() +
                ". The JSON contains formatting issues that could not be automatically fixed. " +
                "Please ensure the JSON is properly formatted with escaped special characters.", e);
        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to parse JSON input: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON array format input.
     * Supports two use cases:
     * 1. Array of items to process (script will receive JSON array as input)
     * 2. Array where first element is config object with script info
     */
    private ScriptInfo parseJsonArrayFormat(String json) throws CapabilityExecutionException {
        try {
            JsonNode arrayNode = objectMapper.readTree(json);

            if (!arrayNode.isArray() || arrayNode.size() == 0) {
                throw new CapabilityExecutionException("JSON array is empty");
            }

            // Check if first element is a script configuration object
            JsonNode firstElement = arrayNode.get(0);
            if (firstElement.isObject() && firstElement.has("script")) {
                // First element contains script config, rest is data
                String scriptType = firstElement.has("scriptType")
                    ? firstElement.get("scriptType").asText("bash")
                    : "bash";

                String script = firstElement.get("script").asText();

                String description = firstElement.has("description")
                    ? firstElement.get("description").asText("Process array data")
                    : "Process array data";

                if (script == null || script.isEmpty()) {
                    throw new CapabilityExecutionException("Script code is required");
                }

                // If there are additional elements, they are data to process
                // We could pass them as environment variable or stdin
                if (arrayNode.size() > 1) {
                    logger.info("Array contains {} data elements to process", arrayNode.size() - 1);
                    // For now, just note this - could be enhanced to pass data to script
                }

                return new ScriptInfo(scriptType, script, description);
            } else {
                // Entire array is data - create a script to process it
                // Convert array to JSON string that can be used in script
                String arrayJson = objectMapper.writeValueAsString(arrayNode);

                // Create a simple script that processes the JSON array
                // Default to Python for JSON processing
                String script = String.format(
                    "import json\n" +
                    "import sys\n" +
                    "data = %s\n" +
                    "# Process the data array\n" +
                    "print(json.dumps(data, indent=2))",
                    arrayJson
                );

                logger.info("Created Python script to process JSON array with {} elements", arrayNode.size());

                return new ScriptInfo("python3", script,
                    "Process JSON array with " + arrayNode.size() + " elements");
            }

        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            // Log the problematic JSON
            logger.error("❌ JSON ARRAY PARSE ERROR at line {}, column {}",
                        e.getLocation() != null ? e.getLocation().getLineNr() : "?",
                        e.getLocation() != null ? e.getLocation().getColumnNr() : "?");

            if (e.getLocation() != null && e.getLocation().getColumnNr() > 0) {
                int errorPos = (int) e.getLocation().getColumnNr() - 1;
                int start = Math.max(0, errorPos - 50);
                int end = Math.min(json.length(), errorPos + 50);

                String context = json.substring(start, end);
                logger.error("Context around error position {}:", errorPos);
                logger.error("  '{}'", context.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
                logger.error("  {}^ ERROR HERE", " ".repeat(Math.min(50, errorPos - start)));
            }

            throw new CapabilityExecutionException(
                "Failed to parse JSON array: " + e.getMessage() +
                ". The JSON likely contains unescaped newlines or special characters.", e);
        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to parse JSON array: " + e.getMessage(), e);
        }
    }

    /**
     * Attempt to fix common JSON formatting issues by escaping unescaped control characters
     * inside string values.
     *
     * This handles the common case where LLMs generate JSON with literal newlines, tabs, etc.
     * inside string values instead of properly escaping them as \n, \t, etc.
     */
    private String fixMalformedJson(String json) {
        logger.debug("Attempting to fix malformed JSON...");

        StringBuilder fixed = new StringBuilder(json.length() + 100);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            // Track if we're inside a string value
            if (c == '"' && !escaped) {
                inString = !inString;
                fixed.append(c);
                escaped = false;
                continue;
            }

            // Track escape sequences
            if (c == '\\' && !escaped) {
                escaped = true;
                fixed.append(c);
                continue;
            }

            // If we're inside a string and encounter a control character, escape it
            if (inString && !escaped) {
                switch (c) {
                    case '\n':
                        fixed.append("\\n");
                        logger.debug("Fixed unescaped newline at position {}", i);
                        break;
                    case '\r':
                        fixed.append("\\r");
                        logger.debug("Fixed unescaped carriage return at position {}", i);
                        break;
                    case '\t':
                        fixed.append("\\t");
                        logger.debug("Fixed unescaped tab at position {}", i);
                        break;
                    case '\b':
                        fixed.append("\\b");
                        break;
                    case '\f':
                        fixed.append("\\f");
                        break;
                    default:
                        // Check for other control characters (ASCII 0-31)
                        if (c < 32) {
                            // Escape as unicode
                            fixed.append(String.format("\\u%04x", (int) c));
                            logger.debug("Fixed control character (code {}) at position {}", (int) c, i);
                        } else {
                            fixed.append(c);
                        }
                        break;
                }
            } else {
                fixed.append(c);
            }

            escaped = false;
        }

        String result = fixed.toString();
        if (!result.equals(json)) {
            logger.info("✅ Successfully fixed malformed JSON (escaped {} control characters)",
                       result.length() - json.length());
        }

        return result;
    }
    
    /**
     * Parse markdown code block format
     */
    private ScriptInfo parseMarkdownFormat(String markdown) {
        // Extract script type from ```type
        String scriptType = "bash";
        String script = markdown;
        
        int firstBacktick = markdown.indexOf("```");
        if (firstBacktick != -1) {
            int lineEnd = markdown.indexOf("\n", firstBacktick);
            if (lineEnd != -1) {
                String firstLine = markdown.substring(firstBacktick + 3, lineEnd).trim();
                if (!firstLine.isEmpty()) {
                    scriptType = firstLine;
                }
                
                // Extract script content
                int lastBacktick = markdown.lastIndexOf("```");
                if (lastBacktick > firstBacktick) {
                    script = markdown.substring(lineEnd + 1, lastBacktick).trim();
                }
            }
        }
        
        return new ScriptInfo(scriptType, script, "Script from markdown");
    }
    
    /**
     * Execute the script and return the result
     */
    private ScriptExecutionResult executeScript(ScriptInfo scriptInfo) throws Exception {
        // Determine the command to run based on script type
        List<String> command = getCommandForScriptType(scriptInfo.scriptType);

        // Create a temporary file for the script
        File tempScript = createTempScriptFile(scriptInfo);

        try {
            // Add the script file to the command
            command.add(tempScript.getAbsolutePath());

            logger.info("Executing command: {}", String.join(" ", command));

            // Execute the script
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(false);

            Process process = processBuilder.start();

            // Read output and error streams
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            // Read stdout
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    logger.error("Error reading stdout", e);
                }
            });

            // Read stderr
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (Exception e) {
                    logger.error("Error reading stderr", e);
                }
            });

            outputThread.start();
            errorThread.start();

            // Wait for process to complete with timeout
            boolean completed = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                throw new CapabilityExecutionException(
                    "Script execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds"
                );
            }

            // Wait for output threads to finish
            outputThread.join(1000);
            errorThread.join(1000);

            int exitCode = process.exitValue();

            return new ScriptExecutionResult(
                exitCode,
                output.toString().trim(),
                error.toString().trim()
            );

        } finally {
            // Clean up temp file
            try {
                Files.deleteIfExists(tempScript.toPath());
            } catch (Exception e) {
                logger.warn("Failed to delete temp script file: {}", e.getMessage());
            }
        }
    }

    /**
     * Get the command to execute for a given script type
     */
    private List<String> getCommandForScriptType(String scriptType) throws CapabilityExecutionException {
        String type = scriptType.toLowerCase();
        List<String> command = new ArrayList<>();

        switch (type) {
            case "bash":
            case "sh":
                command.add(findExecutable("bash", "sh"));
                return command;

            case "python":
            case "python3":
                // Try python3 first (common on macOS/Linux), fall back to python
                command.add(findExecutable("python3", "python"));
                return command;

            case "javascript":
            case "node":
            case "js":
                command.add(findExecutable("node", "nodejs"));
                return command;

            case "ruby":
            case "rb":
                command.add(findExecutable("ruby"));
                return command;

            case "perl":
            case "pl":
                command.add(findExecutable("perl"));
                return command;

            default:
                throw new CapabilityExecutionException(
                    "Unsupported script type: " + scriptType +
                    ". Supported types: bash, sh, python, python3, javascript, node, js, ruby, perl"
                );
        }
    }

    /**
     * Find an executable in the system PATH, trying multiple alternatives
     */
    private String findExecutable(String... alternatives) throws CapabilityExecutionException {
        for (String executable : alternatives) {
            if (isExecutableAvailable(executable)) {
                logger.debug("Found executable: {}", executable);
                return executable;
            }
        }

        throw new CapabilityExecutionException(
            "None of the required executables found: " + String.join(", ", alternatives) +
            ". Please ensure one of them is installed and available in PATH."
        );
    }

    /**
     * Check if an executable is available in the system PATH
     */
    private boolean isExecutableAvailable(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean completed = process.waitFor(2, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return false;
            }

            // Exit code 0 or 1 is acceptable (some programs return 1 for --version)
            int exitCode = process.exitValue();
            return exitCode == 0 || exitCode == 1;

        } catch (Exception e) {
            logger.debug("Executable '{}' not available: {}", executable, e.getMessage());
            return false;
        }
    }

    /**
     * Create a temporary file for the script
     */
    private File createTempScriptFile(ScriptInfo scriptInfo) throws Exception {
        String extension = getFileExtension(scriptInfo.scriptType);
        File tempFile = File.createTempFile("script_", extension);

        // Write the script content (Jackson already handled JSON unescaping)
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(scriptInfo.script);
        }

        // Make the file executable
        tempFile.setExecutable(true);

        logger.debug("Created temp script file: {} with {} chars",
            tempFile.getAbsolutePath(), scriptInfo.script.length());

        return tempFile;
    }

    /**
     * Get file extension for script type
     */
    private String getFileExtension(String scriptType) {
        String type = scriptType.toLowerCase();
        switch (type) {
            case "bash":
            case "sh":
                return ".sh";
            case "python":
            case "python3":
                return ".py";
            case "javascript":
            case "node":
            case "js":
                return ".js";
            case "ruby":
            case "rb":
                return ".rb";
            case "perl":
            case "pl":
                return ".pl";
            default:
                return ".txt";
        }
    }

    /**
     * Internal class to hold script information
     */
    private static class ScriptInfo {
        final String scriptType;
        final String script;
        final String description;

        ScriptInfo(String scriptType, String script, String description) {
            this.scriptType = scriptType;
            this.script = script;
            this.description = description;
        }
    }

    /**
     * Internal class to hold script execution result
     */
    private static class ScriptExecutionResult {
        final int exitCode;
        final String output;
        final String error;

        ScriptExecutionResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }
}


