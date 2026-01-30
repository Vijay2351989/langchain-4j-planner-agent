package com.krista.kme.agent.usage;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krista.kme.agent.planner.guardrails.GuardrailResult;
import com.krista.kme.agent.planner.guardrails.InputGuardrails;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Krista's ChatLanguageModel wrapper that combines:
 * 1. Token usage tracking and cost calculation
 * 2. Input guardrail enforcement (validates BEFORE calling LLM)
 * 3. Output guardrail validation (validates AFTER LLM response)
 * 
 * This is a proxy/wrapper pattern that intercepts all LLM calls and applies
 * guardrails and tracking transparently.
 */
public class KristaChatLanguageModel implements ChatLanguageModel {

    private static final Logger logger = LoggerFactory.getLogger(KristaChatLanguageModel.class);

    // Shared pricing service instance across all tracked models
    private static final ModelPricingService pricingService = new ModelPricingService();

    private final ChatLanguageModel delegate;
    private final SessionUsageCollector usageCollector;
    private final String agentName;
    private final String agentType;
    private final String modelName;
    private final boolean enforceGuardrails;
    private final java.util.Set<Integer> validCapabilityIds;

    public KristaChatLanguageModel(ChatLanguageModel delegate,
                                   SessionUsageCollector usageCollector,
                                   String agentName,
                                   String agentType,
                                   String modelName) {
        this(delegate, usageCollector, agentName, agentType, modelName, true, null);
    }

    public KristaChatLanguageModel(ChatLanguageModel delegate,
                                   SessionUsageCollector usageCollector,
                                   String agentName,
                                   String agentType,
                                   String modelName,
                                   boolean enforceGuardrails) {
        this(delegate, usageCollector, agentName, agentType, modelName, enforceGuardrails, null);
    }

    public KristaChatLanguageModel(ChatLanguageModel delegate,
                                   SessionUsageCollector usageCollector,
                                   String agentName,
                                   String agentType,
                                   String modelName,
                                   boolean enforceGuardrails,
                                   java.util.Set<Integer> validCapabilityIds) {
        this.delegate = delegate;
        this.usageCollector = usageCollector;
        this.agentName = agentName;
        this.agentType = agentType;
        this.modelName = modelName;
        this.enforceGuardrails = enforceGuardrails;
        this.validCapabilityIds = validCapabilityIds;
    }
    
    @Override
    public Response<dev.langchain4j.data.message.AiMessage> generate(List<ChatMessage> messages) {
        // ✅ STEP 1: INPUT GUARDRAILS - Validate BEFORE calling LLM
        if (enforceGuardrails) {
            String lastUserMessage = extractLastUserMessage(messages);
            if (lastUserMessage != null) {
                GuardrailResult inputCheck = InputGuardrails.validateUserInput(lastUserMessage);
                if (inputCheck.isBlocked()) {
                    logger.warn("🛡️ INPUT BLOCKED by guardrails for {}: {}", agentType, inputCheck.getReason());
                    throw new GuardrailViolationException(
                        "Input validation failed: " + inputCheck.getReason()
                    );
                }
                logger.debug("✅ Input passed guardrails for {}", agentType);
            }
        }
        
        // Build prompt summary for tracking
        String promptSummary = buildPromptSummary(messages);
        
        // ✅ STEP 2: Call the actual LLM
        Response<dev.langchain4j.data.message.AiMessage> response = delegate.generate(messages);
        
        // ✅ STEP 3: OUTPUT GUARDRAILS - Validate AFTER LLM response
        if (enforceGuardrails) {
            String responseText = response.content() != null ? response.content().text() : "";
            if (responseText != null && !responseText.isEmpty()) {
                try {
                    // Parse the JSON response into PlannerResponse
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.krista.kme.agent.planner.PlannerResponse plannerResponse =
                        mapper.readValue(responseText, com.krista.kme.agent.planner.PlannerResponse.class);

                    // Validate overall planner response structure (if validCapabilityIds provided)
                    if (validCapabilityIds != null && plannerResponse.isCapability()) {
                        com.krista.kme.agent.planner.guardrails.GuardrailResult responseCheck =
                            com.krista.kme.agent.planner.guardrails.OutputGuardrails.validatePlannerResponse(
                                plannerResponse,
                                validCapabilityIds
                            );

                        if (responseCheck.isBlocked()) {
                            logger.warn("🛡️ OUTPUT BLOCKED by guardrails for {}: {}", agentType, responseCheck.getReason());
                            throw new GuardrailViolationException(
                                "Output validation failed: " + responseCheck.getReason()
                            );
                        }
                    }

                    // Validate capability-specific input (if it's a capability execution)
                    if (plannerResponse.isCapability() && plannerResponse.getId() > 0) {
                        com.krista.kme.agent.planner.guardrails.GuardrailResult capCheck =
                            com.krista.kme.agent.planner.guardrails.OutputGuardrails.validateCapabilityInput(
                                plannerResponse.getId(),
                                plannerResponse.getInputAsString()
                            );

                        if (capCheck.isBlocked()) {
                            logger.warn("🛡️ OUTPUT BLOCKED by guardrails for {}: {}", agentType, capCheck.getReason());
                            throw new GuardrailViolationException(
                                "Output validation failed: " + capCheck.getReason()
                            );
                        }

                        logger.debug("✅ Output passed all guardrails (capability {})", plannerResponse.getId());
                    }

                } catch (GuardrailViolationException e) {
                    // Re-throw guardrail violations
                    throw e;
                } catch (Exception e) {
                    // Log parsing errors but don't block (structured output parsing happens later)
                    logger.debug("Could not parse LLM response for guardrail validation: {}", e.getMessage());
                }
            }
        }
        
        // ✅ STEP 4: TRACKING - Record token usage and cost
        TokenUsage tokenUsage = response.tokenUsage();
        int inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
        int outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;

        double cost = calculateCost(inputTokens, outputTokens);
        
        String responseText = response.content() != null ? response.content().text() : "";
        
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
        
        if (usageCollector != null) {
            usageCollector.addRecord(record);
        }
        
        logger.debug("💰 LLM call tracked: {} - Input: {} tokens, Output: {} tokens, Cost: ${}", 
                    agentType, inputTokens, outputTokens, String.format("%.6f", cost));
        
        return response;
    }
    
    private String extractLastUserMessage(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.type() == dev.langchain4j.data.message.ChatMessageType.USER) {
                return msg.text();
            }
        }
        
        return null;
    }
    
    private String buildPromptSummary(List<ChatMessage> messages) {
        String lastMessage = extractLastUserMessage(messages);
        if (lastMessage == null) {
            return messages != null && !messages.isEmpty() ? messages.get(messages.size() - 1).text() : "";
        }

        // Truncate if too long
        if (lastMessage.length() > 200) {
            return lastMessage.substring(0, 200) + "...";
        }
        return lastMessage;
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

    /**
     * Custom exception for guardrail violations
     * Thrown when input validation fails to prevent LLM call
     */
    public static class GuardrailViolationException extends RuntimeException {
        public GuardrailViolationException(String message) {
            super(message);
        }
    }
}

