package com.krista.kme.agent.planner.web;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import com.krista.kme.agent.planner.PlannerAgent;
import com.krista.kme.agent.planner.capabilities.AnalyzeDataCapability;
import com.krista.kme.agent.planner.capabilities.ExportToFileCapability;
import com.krista.kme.agent.planner.capabilities.FetchDataCapability;
import com.krista.kme.agent.planner.capabilities.GenerateReportCapability;
import com.krista.kme.agent.planner.capabilities.MathematicsCapability;
import com.krista.kme.agent.planner.capabilities.SendEmailCapability;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Service to manage Planner Agent instances and execution
 */
@Service
public class PlannerService {

    private static final Logger logger = LoggerFactory.getLogger(PlannerService.class);

    private final Map<String, PlannerAgent> sessions = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<Integer>> sessionCapabilities = new ConcurrentHashMap<>();
    private final Map<Integer, Capability> capabilities;
    private final ChatLanguageModel model;
    
    public PlannerService() {
        // Initialize chat model first
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("OPENAI_API_KEY not set. Agent will not function properly.");
        }

        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false)
                .build();

        // Initialize capabilities after model is ready
        this.capabilities = createCapabilities();

        logger.info("PlannerService initialized with {} capabilities", capabilities.size());
    }

    private Map<Integer, Capability> createCapabilities() {
        Map<Integer, Capability> caps = new HashMap<>();
        caps.put(1, new FetchDataCapability());
        caps.put(2, new AnalyzeDataCapability());
        caps.put(3, new GenerateReportCapability());
        caps.put(4, new SendEmailCapability());
        caps.put(5, new ExportToFileCapability());
        caps.put(100, new MathematicsCapability(model));
        return caps;
    }
    
    /**
     * Get or create a planner agent for a session
     */
    public PlannerAgent getOrCreateAgent(String sessionId) {
        return getOrCreateAgent(sessionId, null);
    }

    /**
     * Get or create a planner agent for a session with selected capabilities
     */
    public PlannerAgent getOrCreateAgent(String sessionId, java.util.List<Integer> selectedCapabilityIds) {
        // Check if capabilities have changed
        java.util.List<Integer> previousCapabilities = sessionCapabilities.get(sessionId);
        boolean capabilitiesChanged = false;

        if (previousCapabilities == null && selectedCapabilityIds != null) {
            capabilitiesChanged = true;
        } else if (previousCapabilities != null && selectedCapabilityIds == null) {
            capabilitiesChanged = true;
        } else if (previousCapabilities != null && selectedCapabilityIds != null) {
            // Compare the lists
            if (previousCapabilities.size() != selectedCapabilityIds.size() ||
                !previousCapabilities.containsAll(selectedCapabilityIds)) {
                capabilitiesChanged = true;
            }
        }

        // If capabilities changed, remove the old agent
        if (capabilitiesChanged && sessions.containsKey(sessionId)) {
            logger.info("Capabilities changed for session: {}, recreating agent", sessionId);
            sessions.remove(sessionId);
        }

        // Store the current capability selection
        if (selectedCapabilityIds != null) {
            sessionCapabilities.put(sessionId, new java.util.ArrayList<>(selectedCapabilityIds));
        } else {
            sessionCapabilities.remove(sessionId);
        }

        return sessions.computeIfAbsent(sessionId, id -> {
            String systemPrompt =
                "You are a task planner. Break down user requests into sequential capability executions. " +
                "Choose the most appropriate capability for each step. " +
                "IMPORTANT: Always provide the 'input' field with the data needed by each capability.";

            // Filter capabilities if selection is provided
            Map<Integer, Capability> availableCapabilities = capabilities;
            if (selectedCapabilityIds != null && !selectedCapabilityIds.isEmpty()) {
                availableCapabilities = new HashMap<>();
                for (Integer capId : selectedCapabilityIds) {
                    if (capabilities.containsKey(capId)) {
                        availableCapabilities.put(capId, capabilities.get(capId));
                    }
                }
                logger.info("Created new PlannerAgent for session: {} with {} selected capabilities",
                           sessionId, availableCapabilities.size());
            } else {
                logger.info("Created new PlannerAgent for session: {} with all capabilities", sessionId);
            }

            return new PlannerAgent(model, systemPrompt, availableCapabilities);
        });
    }
    
    /**
     * Execute a capability and return the result
     */
    public CapabilityResult executeCapability(int capabilityId, String input) throws CapabilityExecutionException {
        Capability capability = capabilities.get(capabilityId);
        if (capability == null) {
            throw new CapabilityExecutionException("Unknown capability ID: " + capabilityId);
        }
        
        logger.info("Executing capability {} with input length: {}", capabilityId, 
                   input != null ? input.length() : 0);
        
        return capability.execute(input);
    }
    
    /**
     * Get capability information
     */
    public Capability getCapability(int capabilityId) {
        return capabilities.get(capabilityId);
    }
    
    /**
     * Clear a session
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        sessionCapabilities.remove(sessionId);
        logger.info("Cleared session: {}", sessionId);
    }
    
    /**
     * Get all capabilities
     */
    public Map<Integer, Capability> getCapabilities() {
        return new HashMap<>(capabilities);
    }
}

