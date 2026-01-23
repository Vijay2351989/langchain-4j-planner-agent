package com.krista.kme.agent.usage;

import java.time.LocalDateTime;

/**
 * Record of a single LLM call with token usage and cost information
 */
public class LLMUsageRecord {
    private final String agentName;
    private final String agentType;  // "Planner", "MethodFinder", "ScriptCorrection"
    private final LocalDateTime timestamp;
    private final String prompt;
    private final String response;
    private final int inputTokens;
    private final int outputTokens;
    private final double cost;
    private final String modelName;
    
    public LLMUsageRecord(String agentName, String agentType, LocalDateTime timestamp,
                         String prompt, String response, int inputTokens, int outputTokens,
                         double cost, String modelName) {
        this.agentName = agentName;
        this.agentType = agentType;
        this.timestamp = timestamp;
        this.prompt = prompt;
        this.response = response;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cost = cost;
        this.modelName = modelName;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public String getAgentType() {
        return agentType;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public String getResponse() {
        return response;
    }
    
    public int getInputTokens() {
        return inputTokens;
    }
    
    public int getOutputTokens() {
        return outputTokens;
    }
    
    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }
    
    public double getCost() {
        return cost;
    }
    
    public String getModelName() {
        return modelName;
    }
    
    @Override
    public String toString() {
        return String.format("LLMUsageRecord[agent=%s, type=%s, inputTokens=%d, outputTokens=%d, cost=$%.6f]",
                           agentName, agentType, inputTokens, outputTokens, cost);
    }
}

