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
        sb.append("3. Extract or infer the parameters needed for that method from the task description\n");
        sb.append("4. Return a response with:\n");
        sb.append("   - methodId: The ID of the selected method\n");
        sb.append("   - methodName: The name of the selected method\n");
        sb.append("   - description: Brief explanation of why this method was chosen\n");
        sb.append("   - parameters: The parameters to pass to the method (as a string, e.g., JSON or comma-separated values)\n");
        sb.append("\n");
        sb.append("If no appropriate method can be found, return methodId as null and explain why in the description.\n");
        
        return sb.toString();
    }
}

