package com.krista.kme.agent.planner;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

/**
 * Planner Agent using LangChain4j with structured output.
 *
 * This agent:
 * - Receives a list of capabilities (id -> Capability objects)
 * - Builds its own system prompt internally with capability descriptions and input schemas
 * - Returns structured output indicating next capability to execute
 * - Maintains conversation history for multi-step planning
 * - Can request clarification or indicate task completion
 */
public class PlannerAgent {

    private static final Logger logger = LoggerFactory.getLogger(PlannerAgent.class);

    private final MessageWindowChatMemory memory;
    private final PlannerService plannerService;
    private final Map<Integer, Capability> capabilities;

    /**
     * AI Service interface for structured output.
     * LangChain4j will automatically generate JSON schema from PlannerResponse
     * and parse the LLM's JSON response into the POJO.
     */
    interface PlannerService {
        PlannerResponse plan(String userMessage);
    }
    
    /**
     * Create a new PlannerAgent
     *
     * @param model The chat language model to use
     * @param capabilities Map of capability ID to Capability objects
     */
    public PlannerAgent(ChatLanguageModel model, Map<Integer, Capability> capabilities) {
        this(model, capabilities, 20);
    }

    /**
     * Create a new PlannerAgent with custom message window size
     *
     * @param model The chat language model to use
     * @param capabilities Map of capability ID to Capability objects
     * @param maxMessages Maximum number of messages to keep in memory (default: 20)
     */
    public PlannerAgent(ChatLanguageModel model, Map<Integer, Capability> capabilities, int maxMessages) {
        this.capabilities = capabilities;
        this.memory = MessageWindowChatMemory.withMaxMessages(maxMessages);

        // Add system message FIRST before building AI service
        // This includes the system prompt, capabilities, and response rules
        String systemMessageContent = buildSystemMessage();
        memory.add(SystemMessage.from(systemMessageContent));

        logger.info("PlannerAgent initialized with {} capabilities", capabilities.size());
        logger.info("System message added to memory with {} total messages", memory.messages().size());
        logger.debug("System message:\n{}", systemMessageContent);

        // Create the AI service with structured output AFTER system message is added
        // LangChain4j will automatically use JSON schema for PlannerResponse
        this.plannerService = AiServices.builder(PlannerService.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .build();
    }
    
    /**
     * Plan the next step based on user prompt
     *
     * @param userPrompt The user's request or update
     * @return Structured response indicating next action
     */
    public PlannerResponse plan(String userPrompt) {
        logger.info("Planning for user prompt: {}", userPrompt);
        logger.debug("Memory has {} messages before planning", memory.messages().size());
        logger.debug("Agent has {} capabilities available", capabilities.size());

        // Send the prompt - system message is already in memory
        PlannerResponse response = plannerService.plan(userPrompt);

        logger.info("Planner response: {}", response);
        logger.debug("Memory has {} messages after planning", memory.messages().size());
        return response;
    }
    
    /**
     * Report capability execution result and get next step
     *
     * @param capabilityId The ID of the capability that was executed
     * @param result The result of the capability execution
     * @return Structured response indicating next action
     */
    public PlannerResponse reportAndPlanNext(int capabilityId, String result) {
        String capabilityName = capabilities.containsKey(capabilityId)
            ? capabilities.get(capabilityId).getName()
            : "Unknown";

        // Check if this is a method finder failure or clarification request
        String updatePrompt;
        if (result.contains("METHOD_FINDER_FAILURE") || result.contains("METHOD_FINDER_NEEDS_CLARIFICATION")) {
            updatePrompt = String.format(
                "Capability '%s' (ID: %d) reported an issue:\n\n" +
                "%s\n\n" +
                "DECISION REQUIRED:\n" +
                "1. If the issue indicates MISSING INFORMATION that the user can provide:\n" +
                "   → Return Clarification (id=0) asking the user for the specific missing information\n" +
                "2. If the issue indicates the capability CANNOT HANDLE this type of request:\n" +
                "   → Return Unable (id=-1) explaining why\n" +
                "3. If you can try a DIFFERENT capability or approach:\n" +
                "   → Select that capability\n\n" +
                "Review the original user request and the issue details above to make your decision.",
                capabilityName, capabilityId, result
            );
        } else {
            updatePrompt = String.format(
                "I have completed executing capability '%s' (ID: %d).\n\n" +
                "Result:\n%s\n\n" +
                "NEXT STEP: Review the ORIGINAL user request in the conversation history.\n" +
                "If there are more steps to complete, proceed with the next capability.\n" +
                "If all steps are done, return Complete (id=-2).\n" +
                "What should I do next to fulfill the original request?",
                capabilityName, capabilityId, result
            );
        }

        logger.info("Reporting capability {} execution and requesting next step", capabilityId);
        return plan(updatePrompt);
    }
    
    /**
     * Provide clarification and get next step
     * 
     * @param clarification The clarification provided by the user
     * @return Structured response indicating next action
     */
    public PlannerResponse provideClarification(String clarification) {
        logger.info("Providing clarification: {}", clarification);
        return plan(clarification);
    }
    
    /**
     * Reset the conversation memory and re-add system message
     */
    public void reset() {
        memory.clear();

        // Re-add system message after clearing
        String systemMessageContent = buildSystemMessage();
        memory.add(SystemMessage.from(systemMessageContent));

        logger.info("Planner memory reset");
    }

    /**
     * Get the system message for debugging/testing purposes
     */
    public String getSystemMessage() {
        return buildSystemMessage();
    }

    /**
     * Get the capabilities this agent has access to
     */
    public Map<Integer, Capability> getCapabilities() {
        return new HashMap<>(capabilities);
    }

    /**
     * Get the number of capabilities this agent has
     */
    public int getCapabilityCount() {
        return capabilities.size();
    }
    
    /**
     * Build complete system message with instructions and capabilities.
     * This is sent once at initialization.
     */
    private String buildSystemMessage() {
        StringBuilder sb = new StringBuilder();

        // Core system prompt with capability count
        sb.append("You are a task planner. Your role is to help users accomplish tasks efficiently by selecting the appropriate capability when needed.\n\n");

        sb.append("⚠️ CRITICAL RULES - READ CAREFULLY:\n");
        sb.append(String.format("1. You have access to EXACTLY %d capabilities (listed below in AVAILABLE CAPABILITIES)\n", capabilities.size()));
        sb.append("2. You can ONLY use capability IDs that appear in the AVAILABLE CAPABILITIES list\n");
        sb.append("3. If a user requests an action that requires a capability NOT in your list, return id=-1 (Unable)\n");
        sb.append("4. Do NOT assume capabilities exist - check the AVAILABLE CAPABILITIES list first\n");
        sb.append("5. Just because a capability is available does NOT mean it should be used\n");
        sb.append("6. Only use a capability when the user's request EXPLICITLY requires it\n\n");

        sb.append("DECISION FRAMEWORK (follow in order):\n\n");

        sb.append("Step 1: Can you answer the question directly without any capability?\n");
        sb.append("   - Examples: factual questions, explanations, simple calculations, general knowledge\n");
        sb.append("   - If YES: Return id=-2 (Complete) with the answer in the description\n");
        sb.append("   - DO NOT use capabilities for questions you can answer yourself\n\n");

        sb.append("Step 2: Does the user's request require a capability?\n");
        sb.append("   - Identify what action the user wants to perform\n");
        sb.append("   - Read the DESCRIPTION of each capability in AVAILABLE CAPABILITIES below\n");
        sb.append("   - Match the user's request to a capability based on its DESCRIPTION\n");
        sb.append("   - If a capability MATCHES but you're MISSING INFO: Go to Step 3 (ASK, don't say Unable!)\n");
        sb.append("   - If a capability MATCHES and you have ALL info: Use it\n\n");

        sb.append("   🔧 SPECIAL CASE - ExecuteScript Capability as Fallback:\n");
        sb.append("   If NO specific capability matches BUT you know how to solve the task:\n");
        sb.append("   - Check if ExecuteScript capability (ID 6) is available in your capabilities list\n");
        sb.append("   - If YES: Use ExecuteScript to write and execute a script (bash/python/node/etc.) to solve it\n");
        sb.append("   - If NO ExecuteScript capability: Return id=-1 (Unable)\n");
        sb.append("   \n");
        sb.append("   ExecuteScript is perfect for:\n");
        sb.append("   - File operations (create, read, write, delete files)\n");
        sb.append("   - Data processing (parse JSON, CSV, XML, transform data)\n");
        sb.append("   - System commands (list files, check disk space, etc.)\n");
        sb.append("   - Text manipulation (search, replace, format)\n");
        sb.append("   - Any task you can solve with a script\n\n");

        sb.append("   IMPORTANT EXAMPLES:\n");
        sb.append("   ✅ 'analyze data' → AnalyzeData capability EXISTS → Missing: which data? → Go to Step 3\n");
        sb.append("   ✅ 'send email' → SendEmail capability EXISTS → Missing: to whom? → Go to Step 3\n");
        sb.append("   ✅ 'calculate mean' → Mathematics capability EXISTS → Missing: which numbers? → Go to Step 3\n");
        sb.append("   ✅ 'create a JSON file with user data' → No specific capability, but ExecuteScript can do it → Use ExecuteScript (id=6)\n");
        sb.append("   ✅ 'list all .txt files in current directory' → No specific capability, but ExecuteScript can do it → Use ExecuteScript (id=6)\n");
        sb.append("   ✅ 'parse this CSV and show me the totals' → No specific capability, but ExecuteScript can do it → Use ExecuteScript (id=6)\n");
        sb.append("   ❌ 'book a flight' → NO capability for this AND can't be scripted → Return Unable (id=-1)\n");
        sb.append("   ❌ 'order pizza' → NO capability for this AND can't be scripted → Return Unable (id=-1)\n\n");

        sb.append("Step 3: Are you missing required information to execute a capability?\n");
        sb.append("   - Check the capability's Input Schema for required fields\n");
        sb.append("   - If ANY required field is missing: Return id=0 (Clarification)\n");
        sb.append("   - Ask specifically for the missing information\n");
        sb.append("   - Be helpful: suggest what the user might want to do\n\n");

        sb.append("   CRITICAL: Clarification vs Unable\n");
        sb.append("   ✅ Capability exists + missing info = CLARIFICATION (id=0)\n");
        sb.append("   ❌ No capability exists = UNABLE (id=-1)\n");
        sb.append("   DO NOT return Unable when you just need more information!\n\n");

        // Add capabilities list with schemas
        sb.append("═".repeat(80)).append("\n");
        sb.append(String.format("AVAILABLE CAPABILITIES (%d total)\n", capabilities.size()));
        sb.append("═".repeat(80)).append("\n");
        sb.append("⚠️ THESE ARE THE ONLY CAPABILITIES YOU CAN USE:\n");

        // List capability IDs first for quick reference
        sb.append("Valid capability IDs: ");
        sb.append(capabilities.keySet().stream()
            .sorted()
            .map(String::valueOf)
            .reduce((a, b) -> a + ", " + b)
            .orElse("none"));
        sb.append("\n\n");

        for (Capability cap : capabilities.values()) {
            sb.append(String.format("- ID %d: %s\n", cap.getId(), cap.getName()));
            sb.append(String.format("  Description: %s\n", cap.getDescription()));

            // Add input schema if available
            if (cap.getInputSchema() != null && !cap.getInputSchema().trim().isEmpty()) {
                sb.append("  Input Schema:\n");
                // Indent each line of the schema
                for (String line : cap.getInputSchema().split("\n")) {
                    sb.append("    ").append(line).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("═".repeat(80)).append("\n");

        // Input construction guidelines - STRICT RULES
        sb.append("\n").append("═".repeat(80)).append("\n");
        sb.append("⚠️ CRITICAL: INPUT PARAMETER RULES - NEVER GUESS OR MAKE UP VALUES\n");
        sb.append("═".repeat(80)).append("\n");
        sb.append("INPUT CONSTRUCTION:\n");
        sb.append("- Each capability has an Input Schema that defines required and optional fields\n");
        sb.append("- Construct the 'input' as a JSON object matching the schema\n\n");

        sb.append("WHERE TO GET INPUT VALUES (ONLY 3 SOURCES):\n");
        sb.append("1. EXPLICIT VALUES from the user's current request\n");
        sb.append("   Example: User says 'send email to john@example.com' → Use 'john@example.com'\n\n");

        sb.append("2. DATA FROM PREVIOUS CAPABILITY RESULTS in the conversation history\n");
        sb.append("   Example: Previous capability returned 'Result: 42' → Use 42 in next capability\n\n");

        sb.append("3. ASK THE USER if information is missing (return id=0 for Clarification)\n");
        sb.append("   Example: User says 'send email' → Ask 'To whom? What subject? What content?'\n\n");

        sb.append("🚫 ABSOLUTELY FORBIDDEN - NEVER DO THESE:\n");
        sb.append("- ❌ NEVER use placeholder values like 'user@example.com', 'Subject here', 'Content here'\n");
        sb.append("- ❌ NEVER use example values like 'test@test.com', 'Hello', 'Sample message'\n");
        sb.append("- ❌ NEVER guess or infer values that weren't explicitly provided\n");
        sb.append("- ❌ NEVER use default values unless they're in the schema as defaults\n");
        sb.append("- ❌ NEVER make up email addresses, names, numbers, or any other data\n");
        sb.append("- ❌ NEVER assume what the user wants - if unclear, ASK (id=0)\n\n");

        sb.append("✅ CORRECT BEHAVIOR:\n");
        sb.append("- If user's request is missing ANY required field → return id=0 (Clarification)\n");
        sb.append("- If you're unsure about ANY value → return id=0 (Clarification)\n");
        sb.append("- Only proceed with capability execution when you have REAL, ACTUAL values\n");
        sb.append("- Be specific in clarification questions - ask for exactly what's missing\n\n");

        sb.append("EXAMPLES:\n");
        sb.append("❌ WRONG: User says 'send email' → You use {to: 'user@example.com', subject: 'Hello'}\n");
        sb.append("✅ CORRECT: User says 'send email' → You return id=0, description='To whom should I send the email? What should be the subject and content?'\n\n");

        sb.append("❌ WRONG: User says 'calculate mean' → You use {numbers: [1,2,3]}\n");
        sb.append("✅ CORRECT: User says 'calculate mean' → You return id=0, description='Which numbers would you like me to calculate the mean of?'\n\n");

        sb.append("❌ WRONG: User says 'send report to manager' → You use {to: 'manager@company.com'}\n");
        sb.append("✅ CORRECT: User says 'send report to manager' → You return id=0, description='What is your manager's email address?'\n\n");

        // Multi-step task handling
        sb.append("MULTI-STEP TASK HANDLING:\n");
        sb.append("- When you receive a capability execution result, review the ENTIRE conversation history\n");
        sb.append("- Look back at the ORIGINAL user request to understand the COMPLETE goal\n");
        sb.append("- The original request may have MULTIPLE steps (e.g., 'clean data AND send email')\n");
        sb.append("- After completing ONE step, check if there are MORE steps remaining in the original request\n");
        sb.append("- Continue with the next step automatically - DO NOT ask for clarification if the next step is clear\n");
        sb.append("- Only ask for clarification if the original request is ambiguous or missing required information\n");
        sb.append("- Only return Complete (id=-2) when ALL steps in the original request are finished\n\n");

        // Data passing
        sb.append("PASSING DATA BETWEEN CAPABILITIES:\n");
        sb.append("- When a capability returns data, you will see it in the conversation history\n");
        sb.append("- To pass this data to the next capability, include it directly in the 'input' field\n");
        sb.append("- Extract ACTUAL values from previous results - NEVER use placeholder text\n");
        sb.append("- If previous result doesn't contain the data you need → Ask user (id=0)\n\n");

        sb.append("EXAMPLE - Multi-step with data passing:\n");
        sb.append("User: 'Calculate mean of 10, 20, 30 and email the result to john@example.com'\n");
        sb.append("Step 1: Execute Mathematics → Result: '20.00'\n");
        sb.append("Step 2: Execute SendEmail with {to: 'john@example.com', subject: 'Mean Calculation Result', body: 'The mean is 20.00'}\n");
        sb.append("✅ CORRECT: Used actual email from user's request and actual result from previous capability\n\n");

        sb.append("COUNTER-EXAMPLE - What NOT to do:\n");
        sb.append("User: 'Calculate mean of 10, 20, 30 and email the result'\n");
        sb.append("❌ WRONG: Execute SendEmail with {to: 'user@example.com', ...}\n");
        sb.append("✅ CORRECT: Return id=0, description='To whom should I email the result?'\n\n");

        // Handling capability failures
        sb.append("HANDLING CAPABILITY FAILURES:\n");
        sb.append("- Some capabilities (like Mathematics) use sub-agents to find the right method\n");
        sb.append("- If a capability reports 'METHOD_FINDER_FAILURE' or 'METHOD_FINDER_NEEDS_CLARIFICATION':\n");
        sb.append("  1. Read the failure message carefully - it explains what's missing or unclear\n");
        sb.append("  2. If the user can provide the missing information → Return Clarification (id=0)\n");
        sb.append("  3. If the capability simply cannot handle this request → Return Unable (id=-1)\n");
        sb.append("  4. If you can try a different capability → Select that capability\n");
        sb.append("- Example: 'METHOD_FINDER_NEEDS_CLARIFICATION: Missing Information: Which numbers to add?'\n");
        sb.append("  → Return id=0 with description='Which numbers would you like me to add?'\n\n");

        // Final reminder
        sb.append("═".repeat(80)).append("\n");
        sb.append("⚠️ FINAL REMINDER:\n");
        sb.append(String.format("You have EXACTLY %d capabilities available (IDs: %s)\n",
            capabilities.size(),
            capabilities.keySet().stream()
                .sorted()
                .map(String::valueOf)
                .reduce((a, b) -> a + ", " + b)
                .orElse("none")));
        sb.append("\nBEFORE responding:\n");
        sb.append("1. Check if the user's request matches ANY capability DESCRIPTION above\n");
        sb.append("2. If it matches and you have all required info → Use that capability\n");
        sb.append("3. If it matches but you need more info → Ask for clarification (id=0)\n");
        sb.append("4. If NO specific capability matches BUT you can solve it with a script:\n");
        sb.append("   → Check if ExecuteScript (id=6) is available → Use it to write and run the script\n");
        sb.append("5. If NO capability matches AND can't be scripted → Return Unable (id=-1)\n");
        sb.append("6. Do NOT hallucinate or assume capabilities exist - only use the IDs listed above!\n\n");

        sb.append("🚨 CRITICAL DISTINCTION - CLARIFICATION vs UNABLE:\n");
        sb.append("═".repeat(80)).append("\n");
        sb.append("Use CLARIFICATION (id=0) when:\n");
        sb.append("- ✅ A capability EXISTS that can handle the request\n");
        sb.append("- ✅ BUT you're missing required input parameters\n");
        sb.append("- ✅ The user CAN provide the missing information\n");
        sb.append("- Example: 'analyze data' → Capability exists, but WHICH data? → Ask (id=0)\n");
        sb.append("- Example: 'send email' → Capability exists, but TO WHOM? → Ask (id=0)\n");
        sb.append("- Example: 'calculate mean' → Capability exists, but WHICH NUMBERS? → Ask (id=0)\n\n");

        sb.append("Use UNABLE (id=-1) when:\n");
        sb.append("- ❌ NO capability exists that can handle the request\n");
        sb.append("- ❌ The request is completely outside your capabilities\n");
        sb.append("- ❌ The task CANNOT be solved with a script (requires external services, APIs, human interaction)\n");
        sb.append("- Example: 'book a flight' → No capability AND can't be scripted → Unable (id=-1)\n");
        sb.append("- Example: 'order pizza' → No capability AND can't be scripted → Unable (id=-1)\n\n");

        sb.append("Use EXECUTESCRIPT (id=6) when:\n");
        sb.append("- ✅ NO specific capability exists BUT you can solve it with a script\n");
        sb.append("- ✅ The task involves file operations, data processing, system commands, text manipulation\n");
        sb.append("- ✅ You know how to write the script to accomplish the task\n");
        sb.append("- Example: 'create a JSON file with this data' → No specific capability BUT can script it → ExecuteScript (id=6)\n");
        sb.append("- Example: 'count lines in all .txt files' → No specific capability BUT can script it → ExecuteScript (id=6)\n");
        sb.append("- Example: 'parse this CSV and calculate totals' → No specific capability BUT can script it → ExecuteScript (id=6)\n\n");

        sb.append("COMMON MISTAKE TO AVOID:\n");
        sb.append("❌ WRONG: User says 'analyze data' → You return Unable (id=-1) because no data provided\n");
        sb.append("✅ CORRECT: User says 'analyze data' → You return Clarification (id=0) asking 'What data would you like me to analyze? Please provide the data or tell me where to fetch it from.'\n\n");

        sb.append("❌ WRONG: User says 'generate report' → You return Unable (id=-1) because no analysis data\n");
        sb.append("✅ CORRECT: User says 'generate report' → You return Clarification (id=0) asking 'What data should I include in the report? Should I first fetch and analyze some data?'\n\n");

        sb.append("🚨 CRITICAL RULE - NEVER GUESS INPUT PARAMETERS:\n");
        sb.append("- ONLY use values explicitly provided by the user or from previous capability results\n");
        sb.append("- If ANY required parameter is missing → STOP and ask for clarification (id=0)\n");
        sb.append("- NEVER use placeholder, example, or made-up values\n");
        sb.append("- When in doubt → ASK THE USER (id=0)\n");
        sb.append("- If capability exists but info is missing → CLARIFICATION (id=0), NOT Unable (id=-1)\n");
        sb.append("═".repeat(80)).append("\n");

        return sb.toString();
    }
}

