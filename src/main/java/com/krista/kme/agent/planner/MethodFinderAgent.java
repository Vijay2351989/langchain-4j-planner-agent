package com.krista.kme.agent.planner;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

/**
 * Agent that finds the right method within a CompositeCapability.
 * 
 * This agent:
 * - Receives a task description from the planner
 * - Receives a list of available methods in the capability
 * - Returns which method to use and what parameters to pass
 * 
 * This is a stateless agent (no memory) since each method selection is independent.
 */
public class MethodFinderAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(MethodFinderAgent.class);
    
    private final MethodFinderService methodFinderService;
    
    /**
     * AI Service interface for structured output
     */
    interface MethodFinderService {
        MethodFinderResponse findMethod(String prompt);
    }
    
    /**
     * Create a new MethodFinderAgent
     * 
     * @param model The chat language model to use
     */
    public MethodFinderAgent(ChatLanguageModel model) {
        this.methodFinderService = AiServices.builder(MethodFinderService.class)
                .chatLanguageModel(model)
                .build();
        
        logger.info("MethodFinderAgent initialized");
    }
    
    /**
     * Find the appropriate method for a task
     * 
     * @param capabilityName Name of the capability
     * @param taskDescription What the planner wants this capability to do
     * @param methods Available methods in the capability
     * @return Response indicating which method to use and with what parameters
     */
    public MethodFinderResponse findMethod(String capabilityName, String taskDescription, Map<String, CapabilityMethod> methods) {
        logger.info("Finding method in capability '{}' for task: {}", capabilityName, taskDescription);
        
        String prompt = buildPrompt(capabilityName, taskDescription, methods);
        
        MethodFinderResponse response = methodFinderService.findMethod(prompt);
        
        logger.info("Method finder response: {}", response);
        return response;
    }
    
    /**
     * Build the prompt for method selection
     */
    private String buildPrompt(String capabilityName, String taskDescription, Map<String, CapabilityMethod> methods) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("You are a method selector for the '").append(capabilityName).append("' capability.\n\n");
        
        sb.append("TASK DESCRIPTION:\n");
        sb.append(taskDescription).append("\n\n");
        
        sb.append("AVAILABLE METHODS:\n");
        for (CapabilityMethod method : methods.values()) {
            sb.append(String.format("- ID: %s | Name: %s | Description: %s\n", 
                method.getId(), method.getName(), method.getDescription()));
        }
        
        sb.append("\nYOUR JOB:\n");
        sb.append("1. Analyze the task description\n");
        sb.append("2. Select the most appropriate method from the available methods\n");
        sb.append("3. Extract parameters ONLY from the task description - NEVER guess or make up values\n");
        sb.append("4. Assess your confidence in the method selection and parameter extraction\n");
        sb.append("5. Return a response with:\n");
        sb.append("   - methodId: The ID of the selected method (or null if no method matches)\n");
        sb.append("   - methodName: The name of the selected method\n");
        sb.append("   - description: Brief explanation of why this method was chosen\n");
        sb.append("   - parameters: The parameters to pass to the method (as JSON string)\n");
        sb.append("   - confidence: Your confidence level (HIGH, MEDIUM, LOW, or NONE)\n");
        sb.append("     * HIGH: Method clearly identified, all parameters present and certain\n");
        sb.append("     * MEDIUM: Method identified, but some parameters are inferred from context\n");
        sb.append("     * LOW: Method identified, but parameters are uncertain or missing\n");
        sb.append("     * NONE: Cannot identify appropriate method\n");
        sb.append("   - missingInfo: What information is missing or unclear (if any)\n");
        sb.append("   - needsClarification: true if the planner should ask the user for more information\n");
        sb.append("\n");
        sb.append("🚨 CRITICAL RULE - NEVER GUESS PARAMETERS:\n");
        sb.append("- ONLY extract parameters that are EXPLICITLY present in the task description\n");
        sb.append("- If numbers/values are missing → Set confidence=LOW, needsClarification=true\n");
        sb.append("- NEVER use example values like [1,2,3], [10,20,30], or any made-up numbers\n");
        sb.append("- NEVER assume default values unless they're explicitly in the method definition\n");
        sb.append("- If you cannot find the required parameters → Set confidence=LOW or NONE\n");
        sb.append("\n");
        sb.append("EXAMPLES:\n");
        sb.append("❌ WRONG: Task='Calculate mean' → parameters={\"numbers\":[1,2,3]} (NEVER make up numbers!)\n");
        sb.append("✅ CORRECT: Task='Calculate mean' → confidence=LOW, needsClarification=true, missingInfo='Which numbers?'\n\n");
        sb.append("❌ WRONG: Task='Add numbers' → parameters={\"numbers\":[10,20]} (NEVER guess values!)\n");
        sb.append("✅ CORRECT: Task='Add numbers' → confidence=LOW, needsClarification=true, missingInfo='Which numbers to add?'\n\n");
        sb.append("✅ CORRECT: Task='Calculate mean of 5, 10, 15' → parameters={\"numbers\":[5,10,15]}, confidence=HIGH\n");
        sb.append("✅ CORRECT: Task='Add 25 and 75' → parameters={\"numbers\":[25,75]}, confidence=HIGH\n");
        sb.append("\n");
        sb.append("IMPORTANT GUIDELINES:\n");
        sb.append("- If no appropriate method can be found, set methodId=null, confidence=NONE, needsClarification=true\n");
        sb.append("- If method is found but parameters are missing, set confidence=LOW, needsClarification=true\n");
        sb.append("- If method is found but parameters are unclear, set confidence=MEDIUM, needsClarification=true\n");
        sb.append("- Only set confidence=HIGH when ALL parameters are explicitly present in the task description\n");
        sb.append("- When needsClarification=true, be specific in missingInfo about what's needed\n");
        
        return sb.toString();
    }
}

