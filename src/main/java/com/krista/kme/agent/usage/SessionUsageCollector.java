package com.krista.kme.agent.usage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects LLM usage records for a session and provides aggregated statistics
 */
public class SessionUsageCollector {

    private static final Logger logger = LoggerFactory.getLogger(SessionUsageCollector.class);

    private final String sessionId;
    private final List<LLMUsageRecord> records = new ArrayList<>();
    private final Map<String, AgentUsageSummary> agentSummaries = new HashMap<>();

    // LLM Configuration info
    private String llmProvider;
    private String llmModel;
    private String llmApiKeyMasked;

    public SessionUsageCollector(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Set LLM configuration information for this session
     */
    public void setLLMConfiguration(String provider, String model, String apiKey) {
        this.llmProvider = provider;
        this.llmModel = model;
        this.llmApiKeyMasked = maskApiKey(apiKey);
        logger.debug("LLM config set for session {}: provider={}, model={}", sessionId, provider, model);
    }

    /**
     * Mask API key for security (show only last 4 characters)
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "Not set";
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    public String getLlmProvider() {
        return llmProvider != null ? llmProvider : "Unknown";
    }

    public String getLlmModel() {
        return llmModel != null ? llmModel : "Unknown";
    }

    public String getLlmApiKeyMasked() {
        return llmApiKeyMasked != null ? llmApiKeyMasked : "Not set";
    }
    
    /**
     * Add a usage record for this session
     */
    public synchronized void addRecord(LLMUsageRecord record) {
        records.add(record);
        
        // Update agent summary
        String agentKey = record.getAgentType();
        AgentUsageSummary summary = agentSummaries.computeIfAbsent(
            agentKey, 
            k -> new AgentUsageSummary(agentKey)
        );
        summary.addRecord(record);
        
        logger.debug("Added usage record for session {}: {}", sessionId, record);
    }
    
    /**
     * Get all records for this session
     */
    public List<LLMUsageRecord> getRecords() {
        return new ArrayList<>(records);
    }
    
    /**
     * Get usage summary by agent type
     */
    public Map<String, AgentUsageSummary> getAgentSummaries() {
        return new HashMap<>(agentSummaries);
    }
    
    /**
     * Get total input tokens across all agents
     */
    public int getTotalInputTokens() {
        return records.stream().mapToInt(LLMUsageRecord::getInputTokens).sum();
    }
    
    /**
     * Get total output tokens across all agents
     */
    public int getTotalOutputTokens() {
        return records.stream().mapToInt(LLMUsageRecord::getOutputTokens).sum();
    }
    
    /**
     * Get total tokens (input + output) across all agents
     */
    public int getTotalTokens() {
        return getTotalInputTokens() + getTotalOutputTokens();
    }
    
    /**
     * Get total cost across all agents
     */
    public double getTotalCost() {
        return records.stream().mapToDouble(LLMUsageRecord::getCost).sum();
    }
    
    /**
     * Get number of LLM calls made
     */
    public int getCallCount() {
        return records.size();
    }
    
    /**
     * Summary of usage for a specific agent type
     */
    public static class AgentUsageSummary {
        private final String agentType;
        private int callCount = 0;
        private int totalInputTokens = 0;
        private int totalOutputTokens = 0;
        private double totalCost = 0.0;
        
        public AgentUsageSummary(String agentType) {
            this.agentType = agentType;
        }
        
        public void addRecord(LLMUsageRecord record) {
            callCount++;
            totalInputTokens += record.getInputTokens();
            totalOutputTokens += record.getOutputTokens();
            totalCost += record.getCost();
        }
        
        public String getAgentType() {
            return agentType;
        }
        
        public int getCallCount() {
            return callCount;
        }
        
        public int getTotalInputTokens() {
            return totalInputTokens;
        }
        
        public int getTotalOutputTokens() {
            return totalOutputTokens;
        }
        
        public int getTotalTokens() {
            return totalInputTokens + totalOutputTokens;
        }
        
        public double getTotalCost() {
            return totalCost;
        }
        
        @Override
        public String toString() {
            return String.format("AgentUsageSummary[type=%s, calls=%d, inputTokens=%d, outputTokens=%d, cost=$%.6f]",
                               agentType, callCount, totalInputTokens, totalOutputTokens, totalCost);
        }
    }
}

