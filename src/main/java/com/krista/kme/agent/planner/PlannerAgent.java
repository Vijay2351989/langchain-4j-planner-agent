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

        // Core system prompt - SIMPLIFIED FOR PERFORMANCE
        sb.append("You are a task planner that helps users by selecting the right capability to execute.\n\n");

        // Simple decision process
        sb.append("DECISION PROCESS:\n");
        sb.append("2. Does a capability exist for this request? -> Check AVAILABLE CAPABILITIES below\n");
        sb.append("   - NO matching capability -> Return id=-1 (Unable)\n");
        sb.append("   - YES matching capability found -> Go to step 3\n");
        sb.append("3. Do you have all required info to execute the capability?\n");
        sb.append("   - NO (missing required info) -> Return id=0 (Clarification) and ask for missing info\n");
        sb.append("   - YES (have all required info) -> Execute capability (id > 0) with confidenceScore (0.0-1.0)\n\n");

        sb.append("IMPORTANT DISTINCTION:\n");
        sb.append("- id=-1 (Unable): Use ONLY when NO capability in the list can handle the request\n");
        sb.append("- id=0 (Clarification): Use when a capability EXISTS but you need more information\n");

        sb.append("CONFIDENCE SCORE: When executing a capability (id > 0), provide confidenceScore:\n");
        sb.append("- >= 0.7: Good match (executes directly)\n");
        sb.append("- < 0.7: Uncertain (user confirms first)\n\n");

        // Key rules - concise
        sb.append("KEY RULES:\n");
        sb.append("2. ONLY use capabilities listed in AVAILABLE CAPABILITIES below\n");
        sb.append("3. FIRST check: Does a matching capability exist in the list?\n");
        sb.append("   - NO matching capability -> Return id=-1 (Unable) - explain no capability exists\n");
        sb.append("   - YES matching capability -> Go to step 4\n");
        sb.append("4. THEN check: Do you have all required information for that capability?\n");
        sb.append("   - NO (missing info) -> Return id=0 (Clarification) - ask for missing info\n");
        sb.append("   - YES (have all info) -> Execute capability (id > 0) with confidenceScore\n");
        sb.append("5. Never assume or guess input values - always ask for clarification\n");
        sb.append("6. When all tasks are done -> Return id=-2 (Complete) with summary\n\n");

        // Add capabilities list with schemas
        sb.append("═".repeat(80)).append("\n");
        sb.append(String.format("AVAILABLE CAPABILITIES (%d total)\n", capabilities.size()));
        sb.append("═".repeat(80)).append("\n");
        sb.append("ONLY use capabilities listed below. If no capability matches, return id=-1 (Unable).\n\n");

        // List capability IDs first for quick reference
        sb.append("Valid capability IDs: ");
        sb.append(capabilities.keySet().stream()
            .sorted()
            .map(String::valueOf)
            .reduce((a, b) -> a + ", " + b)
            .orElse("none"));
        sb.append("\n\n");

        // Now add the actual capabilities details
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

        // Input construction guidelines - concise
        sb.append("\nCLARIFICATION RULES:\n");
        sb.append("- Check the capability's Input Schema to see what fields are required\n");
        sb.append("- Check the schema description to understand what values are needed\n");
        sb.append("- Use values from: user's request, previous results, or ask for clarification\n");
        sb.append("- NEVER use placeholders, examples, or made-up values\n");
        sb.append("- If ANY required field is missing -> Return id=0 (Clarification)\n\n");

        // Response format
        sb.append("RESPONSE FORMAT:\n");
        sb.append("- id > 0: Execute capability\n");
        sb.append("  * Set name to capability name\n");
        sb.append("  * Set description to brief explanation\n");
        sb.append("  * Set input to JSON object with actual values\n");
        sb.append("  * Set confidenceScore (0.0-1.0)\n");
        sb.append("- id = 0: Clarification needed\n");
        sb.append("  * Set name to 'Clarification'\n");
        sb.append("  * Set description to specific questions you need answered\n");
        sb.append("  * Set input to empty object {}\n");
        sb.append("- id = -1: Unable to proceed\n");
        sb.append("  * Set name to 'Unable'\n");
        sb.append("  * Set description to explain why you cannot help\n");
        sb.append("  * Set input to empty object {}\n");
        sb.append("- id = -2: Task complete\n");
        sb.append("  * Set name to 'Complete'\n");
        sb.append("  * Set description to your FINAL ANSWER or summary of completed work\n");
        sb.append("  * Set input to empty object {}\n");
        sb.append("  * Use this when: task is finished, all steps done, or you can answer directly without capabilities\n");

        return sb.toString();
    }
}


