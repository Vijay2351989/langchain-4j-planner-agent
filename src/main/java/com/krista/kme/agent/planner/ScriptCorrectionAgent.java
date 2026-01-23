package com.krista.kme.agent.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

/**
 * Agent that fixes failing scripts based on error messages.
 * 
 * This agent:
 * - Receives a failed script and its error message
 * - Analyzes the error and identifies the issue
 * - Returns a corrected version of the script
 * 
 * This is a stateless agent (no memory) since each correction is independent.
 */
public class ScriptCorrectionAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptCorrectionAgent.class);
    
    private final ScriptCorrectionService scriptCorrectionService;
    
    /**
     * AI Service interface for structured output
     */
    interface ScriptCorrectionService {
        ScriptCorrectionResponse correctScript(String prompt);
    }
    
    /**
     * Create a new ScriptCorrectionAgent
     * 
     * @param model The chat language model to use
     */
    public ScriptCorrectionAgent(ChatLanguageModel model) {
        this.scriptCorrectionService = AiServices.builder(ScriptCorrectionService.class)
                .chatLanguageModel(model)
                .build();
        
        logger.info("ScriptCorrectionAgent initialized");
    }
    
    /**
     * Fix a failing script based on error message
     * 
     * @param scriptType Type of script (python, bash, etc.)
     * @param failedScript The script that failed
     * @param errorMessage The error message from execution
     * @param taskDescription What the script was supposed to do
     * @return Response with corrected script
     */
    public ScriptCorrectionResponse correctScript(String scriptType, String failedScript, 
                                                   String errorMessage, String taskDescription) {
        logger.info("Correcting {} script. Error: {}", scriptType, 
            errorMessage.length() > 100 ? errorMessage.substring(0, 100) + "..." : errorMessage);
        
        String prompt = buildPrompt(scriptType, failedScript, errorMessage, taskDescription);
        
        ScriptCorrectionResponse response = scriptCorrectionService.correctScript(prompt);
        
        logger.info("Script correction response: {}", response);
        return response;
    }
    
    /**
     * Build the prompt for script correction
     */
    private String buildPrompt(String scriptType, String failedScript, 
                               String errorMessage, String taskDescription) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("You are an expert ").append(scriptType).append(" script debugger and fixer.\n\n");
        
        sb.append("TASK:\n");
        sb.append(taskDescription).append("\n\n");
        
        sb.append("FAILED SCRIPT:\n");
        sb.append("```").append(scriptType).append("\n");
        sb.append(failedScript).append("\n");
        sb.append("```\n\n");
        
        sb.append("ERROR MESSAGE:\n");
        sb.append(errorMessage).append("\n\n");
        
        sb.append("YOUR JOB:\n");
        sb.append("1. Analyze the error message carefully\n");
        sb.append("2. Identify the root cause of the failure\n");
        sb.append("3. Fix ONLY the error - do NOT change the overall logic or approach\n");
        sb.append("4. Return the corrected script\n\n");
        
        sb.append("IMPORTANT GUIDELINES:\n");
        sb.append("- Keep the same scriptType: ").append(scriptType).append("\n");
        sb.append("- Preserve the original logic and approach\n");
        sb.append("- Fix only what's causing the error\n");
        sb.append("- Ensure the script still accomplishes the original task\n");
        sb.append("- If the error is about missing imports, add them\n");
        sb.append("- If the error is about wrong data types, fix the type handling\n");
        sb.append("- If the error is about undefined variables, define them properly\n");
        sb.append("- If the error is about syntax, fix the syntax\n");
        sb.append("- CRITICAL: The script MUST print/output the final result\n");
        sb.append("- If the script defines a function, make sure to CALL it and print the result\n");
        sb.append("- Never leave the output as None - always print the actual result\n\n");
        
        sb.append("RESPONSE FORMAT:\n");
        sb.append("Return a JSON object with these fields:\n");
        sb.append("- scriptType: \"").append(scriptType).append("\"\n");
        sb.append("- script: The corrected script code (as a string)\n");
        sb.append("- description: Brief explanation of what you fixed\n");
        sb.append("- fixApplied: true if you successfully fixed it, false if unfixable\n");
        
        return sb.toString();
    }
}

