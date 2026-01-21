package com.krista.kme.agent.planner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * - Takes a system prompt and user prompt
 * - Receives a list of capabilities (id -> Capability objects)
 * - Returns structured output indicating next capability to execute
 * - Maintains conversation history for multi-step planning
 * - Can request clarification or indicate task completion
 */
public class PlannerAgent {

    private static final Logger logger = LoggerFactory.getLogger(PlannerAgent.class);

    private final MessageWindowChatMemory memory;
    private final PlannerService plannerService;
    private final String systemPrompt;
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
     * @param systemPrompt The system prompt defining agent behavior
     * @param capabilities Map of capability ID to Capability objects
     */
    public PlannerAgent(ChatLanguageModel model, String systemPrompt, Map<Integer, Capability> capabilities) {
        this(model, systemPrompt, capabilities, 20);
    }

    /**
     * Create a new PlannerAgent with custom message window size
     *
     * @param model The chat language model to use
     * @param systemPrompt The system prompt defining agent behavior
     * @param capabilities Map of capability ID to Capability objects
     * @param maxMessages Maximum number of messages to keep in memory (default: 20)
     */
    public PlannerAgent(ChatLanguageModel model, String systemPrompt, Map<Integer, Capability> capabilities, int maxMessages) {
        this.systemPrompt = systemPrompt;
        this.capabilities = capabilities;
        this.memory = MessageWindowChatMemory.withMaxMessages(maxMessages);

        // Create the AI service with structured output
        // LangChain4j will automatically use JSON schema for PlannerResponse
        this.plannerService = AiServices.builder(PlannerService.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .build();

        // Add system message once at initialization
        // This includes the system prompt, capabilities, and response rules
        String systemMessageContent = buildSystemMessage();
        memory.add(SystemMessage.from(systemMessageContent));

        logger.info("PlannerAgent initialized with {} capabilities", capabilities.size());
    }
    
    /**
     * Plan the next step based on user prompt
     *
     * @param userPrompt The user's request or update
     * @return Structured response indicating next action
     */
    public PlannerResponse plan(String userPrompt) {
        return plan(userPrompt, null);
    }

    /**
     * Plan the next step based on user prompt with input variables
     *
     * @param userPrompt The user's request or update
     * @param inputVariables Optional list of input variables providing context
     * @return Structured response indicating next action
     */
    public PlannerResponse plan(String userPrompt, List<InputVariable> inputVariables) {
        logger.info("Planning for user prompt: {}", userPrompt);

        // Build the complete prompt with input variables if provided
        String completePrompt = buildPromptWithVariables(userPrompt, inputVariables);

        if (inputVariables != null && !inputVariables.isEmpty()) {
            logger.info("Planning with {} input variables: {}",
                inputVariables.size(),
                inputVariables.stream()
                    .map(InputVariable::toString)
                    .collect(Collectors.joining(", ")));
        }

        // Send the prompt - system message is already in memory
        PlannerResponse response = plannerService.plan(completePrompt);

        logger.info("Planner response: {}", response);
        return response;
    }

    /**
     * Build a prompt that includes input variables
     *
     * @param userPrompt The base user prompt
     * @param inputVariables Optional input variables
     * @return Complete prompt with variables included
     */
    private String buildPromptWithVariables(String userPrompt, List<InputVariable> inputVariables) {
        if (inputVariables == null || inputVariables.isEmpty()) {
            return userPrompt;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Input Variables:\n");
        for (InputVariable var : inputVariables) {
            prompt.append("- ").append(var.toPromptString()).append("\n");
        }
        prompt.append("\nUser Request:\n");
        prompt.append(userPrompt);

        return prompt.toString();
    }
    
    /**
     * Report capability execution result and get next step
     *
     * @param capabilityId The ID of the capability that was executed
     * @param result The result of the capability execution
     * @return Structured response indicating next action
     */
    public PlannerResponse reportAndPlanNext(int capabilityId, String result) {
        return reportAndPlanNext(capabilityId, result, 500);
    }

    /**
     * Report capability execution result and get next step with result truncation
     *
     * @param capabilityId The ID of the capability that was executed
     * @param result The result of the capability execution
     * @param maxResultLength Maximum length of result to include (to avoid context overflow)
     * @return Structured response indicating next action
     */
    public PlannerResponse reportAndPlanNext(int capabilityId, String result, int maxResultLength) {
        String capabilityName = capabilities.containsKey(capabilityId)
            ? capabilities.get(capabilityId).getName()
            : "Unknown";

        // Truncate result if too long to avoid context overflow
        String truncatedResult = result;
        if (result.length() > maxResultLength) {
            truncatedResult = result.substring(0, maxResultLength) + "... (truncated)";
            logger.warn("Result truncated from {} to {} characters to avoid context overflow",
                result.length(), maxResultLength);
        }

        String updatePrompt = String.format(
            "I have completed executing capability '%s' (ID: %d). Result: %s\n\nWhat should I do next?",
            capabilityName, capabilityId, truncatedResult
        );

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
     * Build system message with instructions and capabilities.
     * This is sent once at initialization.
     */
    private String buildSystemMessage() {
        StringBuilder sb = new StringBuilder();

        // Add system prompt
        sb.append(systemPrompt).append("\n\n");

        // Add capabilities list
        sb.append("AVAILABLE CAPABILITIES:\n");
        for (Capability cap : capabilities.values()) {
            sb.append(String.format("- ID %d: %s - %s\n", cap.getId(), cap.getName(), cap.getDescription()));
        }

        // Add response rules
        sb.append("\nRESPONSE RULES:\n");
        sb.append("1. If you can identify the next capability to use:\n");
        sb.append("   - Return its id, name, and description\n");
        sb.append("   - IMPORTANT: Include 'input' field with the data needed by the capability\n");
        sb.append("   - If input variables are provided in the user request, use them to determine capability inputs\n");
        sb.append("   - If you cannot determine the required input, return id=0 to ask for clarification\n");
        sb.append("   - The input should match what the capability description specifies\n");
        sb.append("2. If you need clarification, return id=0 with name='Clarification' and description containing your question\n");
        sb.append("3. If you cannot identify a suitable capability, return id=-1 with name='Unable' and description explaining why\n");
        sb.append("4. If the task is complete, return id=-2 with name='Complete' and description containing the final answer\n");
        sb.append("\nNOTE: User requests may include 'Input Variables' section with context data (e.g., user_id, date_range, etc.).\n");
        sb.append("Use these variables when determining capability inputs.\n");

        return sb.toString();
    }
}

