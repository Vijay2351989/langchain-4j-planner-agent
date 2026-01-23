package com.krista.kme.agent.usage;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Wrapper around ChatLanguageModel that tracks token usage and costs
 *
 * Uses ModelPricingService to fetch real-time pricing from Portkey Models API
 */
public class TrackedChatLanguageModel implements ChatLanguageModel {

    private static final Logger logger = LoggerFactory.getLogger(TrackedChatLanguageModel.class);

    // Shared pricing service instance across all tracked models
    private static final ModelPricingService pricingService = new ModelPricingService();

    private final ChatLanguageModel delegate;
    private final SessionUsageCollector usageCollector;
    private final String agentName;
    private final String agentType;
    private final String modelName;

    public TrackedChatLanguageModel(ChatLanguageModel delegate,
                                   SessionUsageCollector usageCollector,
                                   String agentName,
                                   String agentType,
                                   String modelName) {
        this.delegate = delegate;
        this.usageCollector = usageCollector;
        this.agentName = agentName;
        this.agentType = agentType;
        this.modelName = modelName;
    }
    
    @Override
    public Response<dev.langchain4j.data.message.AiMessage> generate(List<ChatMessage> messages) {
        // Build prompt summary
        String promptSummary = buildPromptSummary(messages);
        
        // Call the actual model
        Response<dev.langchain4j.data.message.AiMessage> response = delegate.generate(messages);
        
        // Extract token usage
        TokenUsage tokenUsage = response.tokenUsage();
        int inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
        int outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;

        // Calculate cost using dynamic pricing
        double cost = calculateCost(inputTokens, outputTokens);
        
        // Get response text
        String responseText = response.content() != null ? response.content().text() : "";
        
        // Create usage record
        LLMUsageRecord record = new LLMUsageRecord(
            agentName,
            agentType,
            LocalDateTime.now(),
            promptSummary,
            responseText,
            inputTokens,
            outputTokens,
            cost,
            modelName
        );
        
        // Add to collector
        if (usageCollector != null) {
            usageCollector.addRecord(record);
        }
        
        logger.debug("LLM call tracked: {} - Input: {} tokens, Output: {} tokens, Cost: ${}", 
                    agentType, inputTokens, outputTokens, String.format("%.6f", cost));
        
        return response;
    }
    
    private String buildPromptSummary(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        
        // Get the last user message as summary
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.type() == dev.langchain4j.data.message.ChatMessageType.USER) {
                String text = msg.text();
                // Truncate if too long
                if (text.length() > 200) {
                    return text.substring(0, 200) + "...";
                }
                return text;
            }
        }
        
        return messages.get(messages.size() - 1).text();
    }
    
    /**
     * Calculate cost using dynamic pricing from Portkey Models API
     */
    private double calculateCost(int inputTokens, int outputTokens) {
        ModelPricingService.ModelPricing pricing = pricingService.getPricing(modelName);

        double inputCost = (inputTokens / 1_000_000.0) * pricing.inputCostPer1M;
        double outputCost = (outputTokens / 1_000_000.0) * pricing.outputCostPer1M;

        return inputCost + outputCost;
    }
}

