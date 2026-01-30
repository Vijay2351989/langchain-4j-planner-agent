package com.krista.kme.agent.planner.web;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import com.krista.kme.agent.planner.ModelFactory;
import com.krista.kme.agent.planner.ModelFactory.ModelConfig;
import com.krista.kme.agent.planner.ModelFactory.Provider;
import com.krista.kme.agent.planner.PlannerAgent;
import com.krista.kme.agent.planner.capabilities.AnalyzeDataCapability;
import com.krista.kme.agent.planner.capabilities.ExecuteScriptCapability;
import com.krista.kme.agent.planner.capabilities.ExportToFileCapability;
import com.krista.kme.agent.planner.capabilities.FetchDataCapability;
import com.krista.kme.agent.planner.capabilities.GenerateReportCapability;
import com.krista.kme.agent.planner.capabilities.MathematicsCapability;
import com.krista.kme.agent.planner.capabilities.SendEmailCapability;
import com.krista.kme.agent.usage.KristaChatLanguageModel;
import com.krista.kme.agent.usage.SessionUsageCollector;
import com.krista.kme.agent.usage.SessionUsageManager;

import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Service to manage Planner Agent instances and execution
 */
@Service
public class PlannerService {

    private static final Logger logger = LoggerFactory.getLogger(PlannerService.class);

    private final Map<String, PlannerAgent> sessions = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<Integer>> sessionCapabilities = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Capability>> sessionCapabilityInstances = new ConcurrentHashMap<>();
    private final Map<Integer, Capability> staticCapabilities;  // Capabilities that don't need LLM
    private final ChatLanguageModel baseModel;  // Base model without tracking
    private final SessionUsageManager usageManager;
    private final String modelProviderName;  // For tracking purposes
    private final String modelName;  // For tracking purposes
    private final String apiKeyForTracking;  // For usage report (will be masked)

    public PlannerService() {
        // Initialize chat model using ModelFactory
        // Reads from environment variables:
        // - LLM_PROVIDER (e.g., "OPENAI", "ANTHROPIC", "GOOGLE_GEMINI")
        // - LLM_MODEL_NAME (e.g., "gpt-4o-mini", "claude-3-5-sonnet-20241022")
        // - LLM_API_KEY (optional, falls back to provider-specific keys)
        // - Provider-specific keys: OPENAI_API_KEY, ANTHROPIC_API_KEY, GOOGLE_AI_API_KEY

        logger.info("Initializing PlannerService with ModelFactory...");

        // Get configuration from environment
        String providerStr = System.getenv("LLM_PROVIDER");
        String modelNameEnv = System.getenv("LLM_MODEL_NAME");
        String apiKey = System.getenv("LLM_API_KEY");

        // Determine provider (default to OPENAI)
        Provider provider = Provider.OPENAI;
        if (providerStr != null && !providerStr.isEmpty()) {
            try {
                provider = Provider.valueOf(providerStr.toUpperCase());
                logger.info("Using LLM provider from environment: {}", provider);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid LLM_PROVIDER '{}'. Defaulting to OPENAI. Valid values: OPENAI, ANTHROPIC, GOOGLE_GEMINI, AZURE_OPENAI",
                           providerStr);
            }
        } else {
            logger.info("LLM_PROVIDER not set. Defaulting to OPENAI");
        }

        // Determine model name (use provider-specific defaults if not specified)
        if (modelNameEnv == null || modelNameEnv.isEmpty()) {
            switch (provider) {
                case OPENAI:
                    modelNameEnv = "gpt-4o-mini";
                    break;
                case ANTHROPIC:
                    modelNameEnv = "claude-3-5-sonnet-20241022";
                    break;
                case GOOGLE_GEMINI:
                    modelNameEnv = "gemini-1.5-pro";
                    break;
                case AZURE_OPENAI:
                    modelNameEnv = "gpt-4o-mini";
                    break;
            }
            logger.info("LLM_MODEL_NAME not set. Using default for {}: {}", provider, modelNameEnv);
        } else {
            logger.info("Using model from environment: {}", modelNameEnv);
        }

        // Store for tracking
        this.modelProviderName = provider.toString();
        this.modelName = modelNameEnv;

        // Determine which API key is being used (for usage tracking)
        String actualApiKey = apiKey;
        if (actualApiKey == null || actualApiKey.isEmpty()) {
            // Fall back to provider-specific keys
            switch (provider) {
                case OPENAI:
                    actualApiKey = System.getenv("OPENAI_API_KEY");
                    break;
                case ANTHROPIC:
                    actualApiKey = System.getenv("ANTHROPIC_API_KEY");
                    break;
                case GOOGLE_GEMINI:
                    actualApiKey = System.getenv("GOOGLE_AI_API_KEY");
                    break;
                case AZURE_OPENAI:
                    actualApiKey = System.getenv("AZURE_OPENAI_API_KEY");
                    break;
            }
        }
        this.apiKeyForTracking = actualApiKey;

        // Build model configuration
        ModelConfig.Builder configBuilder = ModelConfig.builder()
                .provider(provider)
                .modelName(modelNameEnv)
                .temperature(0.3)
                .timeoutSeconds(60);

        // Add API key if provided via LLM_API_KEY
        if (apiKey != null && !apiKey.isEmpty()) {
            configBuilder.apiKey(apiKey);
            logger.info("Using API key from LLM_API_KEY environment variable");
        }

        // Create the base model
        try {
            this.baseModel = ModelFactory.createModel(configBuilder.build());
            logger.info("✅ Successfully initialized {} model: {}", provider, modelNameEnv);
        } catch (IllegalStateException e) {
            logger.error("❌ Failed to initialize LLM model: {}", e.getMessage());
            logger.error("Please set the appropriate API key environment variable:");
            logger.error("  - For OpenAI: OPENAI_API_KEY or LLM_API_KEY");
            logger.error("  - For Anthropic: ANTHROPIC_API_KEY or LLM_API_KEY");
            logger.error("  - For Google Gemini: GOOGLE_AI_API_KEY or LLM_API_KEY");
            logger.error("  - For Azure OpenAI: AZURE_OPENAI_API_KEY and AZURE_OPENAI_ENDPOINT");
            throw new RuntimeException("Failed to initialize LLM model. Check API key configuration.", e);
        }

        // Initialize usage manager
        this.usageManager = new SessionUsageManager("usage-reports");

        // Initialize static capabilities (ones that don't need LLM)
        this.staticCapabilities = createStaticCapabilities();

        logger.info("PlannerService initialized with {} static capabilities", staticCapabilities.size());
        logger.info("Configuration: Provider={}, Model={}", modelProviderName, modelName);
    }

    /**
     * Create capabilities that don't require LLM (can be shared across sessions)
     */
    private Map<Integer, Capability> createStaticCapabilities() {
        Map<Integer, Capability> caps = new HashMap<>();
        caps.put(1, new FetchDataCapability());
        caps.put(2, new AnalyzeDataCapability());
        caps.put(3, new GenerateReportCapability());
        caps.put(4, new SendEmailCapability());
        caps.put(5, new ExportToFileCapability());
        // Note: ExecuteScriptCapability (6) and MathematicsCapability (100) need LLM
        // They will be created per-session with tracked models
        return caps;
    }

    /**
     * Create all capabilities for a session, including LLM-based ones with tracking
     */
    private Map<Integer, Capability> createSessionCapabilities(String sessionId,
                                                               SessionUsageCollector collector) {
        Map<Integer, Capability> caps = new HashMap<>(staticCapabilities);

        // Create tracked models for capabilities that need LLM
        // Use the actual model name from configuration instead of hardcoded value
        ChatLanguageModel scriptCorrectionModel = new KristaChatLanguageModel(
            baseModel,
            collector,
            "ScriptCorrection-" + sessionId,
            "ScriptCorrection",
            modelName  // Use configured model name
        );

        ChatLanguageModel methodFinderModel = new KristaChatLanguageModel(
            baseModel,
            collector,
            "MethodFinder-" + sessionId,
            "MethodFinder",
            modelName  // Use configured model name
        );

        // Add LLM-based capabilities with tracked models
        caps.put(6, new ExecuteScriptCapability(scriptCorrectionModel));
        caps.put(100, new MathematicsCapability(methodFinderModel));

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
        // Check if agent already exists for this session
        if (sessions.containsKey(sessionId)) {
            // Agent exists - return it and IGNORE any new capability selection
            logger.debug("Reusing existing agent for session: {}", sessionId);
            java.util.List<Integer> lockedCapabilities = sessionCapabilities.get(sessionId);
            logger.debug("Session {} is locked to capabilities: {}", sessionId, lockedCapabilities);
            return sessions.get(sessionId);
        }

        // No agent exists - create new one with the provided capabilities
        logger.info("🆕 Creating NEW PlannerAgent for session: {}", sessionId);
        logger.info("   Capabilities selected: {}", selectedCapabilityIds);

        // Get usage collector for this session
        SessionUsageCollector collector = usageManager.getCollector(sessionId);

        // Set LLM configuration in the collector for usage report
        collector.setLLMConfiguration(modelProviderName, modelName, apiKeyForTracking);

        // Create all capabilities for this session (including LLM-based ones with tracking)
        Map<Integer, Capability> allSessionCapabilities = createSessionCapabilities(sessionId, collector);

        // Filter capabilities based on selection
        Map<Integer, Capability> availableCapabilities;
        if (selectedCapabilityIds != null && !selectedCapabilityIds.isEmpty()) {
            availableCapabilities = new HashMap<>();
            for (Integer capId : selectedCapabilityIds) {
                if (allSessionCapabilities.containsKey(capId)) {
                    availableCapabilities.put(capId, allSessionCapabilities.get(capId));
                }
            }
            logger.info("   ✓ Agent will have {} SELECTED capabilities: {}",
                       availableCapabilities.size(), selectedCapabilityIds);
            logger.info("   ✓ Capability names: {}",
                       availableCapabilities.values().stream()
                           .map(Capability::getName)
                           .toList());

            // Lock these capabilities for this session
            sessionCapabilities.put(sessionId, new java.util.ArrayList<>(selectedCapabilityIds));
        } else {
            // No selection provided - use ALL capabilities
            availableCapabilities = new HashMap<>(allSessionCapabilities);
            logger.warn("   ⚠️ No capabilities selected - using ALL {} capabilities",
                       allSessionCapabilities.size());
            sessionCapabilities.remove(sessionId);
        }

        // Create tracked model for Planner agent with guardrails
        // Use the actual model name from configuration
        // Pass valid capability IDs for output guardrail validation
        ChatLanguageModel plannerModel = new KristaChatLanguageModel(
            baseModel,
            collector,
            "PlannerAgent-" + sessionId,
            "Planner",
            modelName,  // Use configured model name
            true,  // Enable guardrails
            availableCapabilities.keySet()  // Valid capability IDs for output validation
        );

        // Create and store the agent with tracked model
        PlannerAgent agent = new PlannerAgent(plannerModel, availableCapabilities);
        sessions.put(sessionId, agent);

        // Store the full capability instances for this session (for executeCapability)
        sessionCapabilityInstances.put(sessionId, allSessionCapabilities);

        logger.info("   ✓ Agent created and locked for session: {}", sessionId);
        logger.info("   ✓ LLM usage tracking enabled for Planner, ScriptCorrection, and Mathematics");
        return agent;
    }
    
    /**
     * Execute a capability and return the result
     */
    public CapabilityResult executeCapability(int capabilityId, String input) throws CapabilityExecutionException {
        return executeCapability(capabilityId, input, null);
    }

    /**
     * Execute a capability (uses session-specific capability instances with tracking)
     */
    public CapabilityResult executeCapability(int capabilityId, String input, String sessionId) throws CapabilityExecutionException {
        // Get session-specific capabilities (which include tracked LLM models)
        Map<Integer, Capability> sessionCaps = sessionCapabilityInstances.get(sessionId);

        Capability capability;
        if (sessionCaps != null) {
            capability = sessionCaps.get(capabilityId);
        } else {
            // Fallback to static capabilities if session not found
            capability = staticCapabilities.get(capabilityId);
        }

        if (capability == null) {
            throw new CapabilityExecutionException("Unknown capability ID: " + capabilityId);
        }

        logger.info("Executing capability {} for session {} with input length: {}",
                   capabilityId, sessionId,
                   input != null ? input.length() : 0);

        return capability.execute(input);
    }

    /**
     * Get capability information (returns static capability info)
     */
    public Capability getCapability(int capabilityId) {
        return staticCapabilities.get(capabilityId);
    }
    
    /**
     * Get an existing session (returns null if not found)
     */
    public PlannerAgent getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Clear a session - removes the agent, unlocks capabilities, and generates usage report
     */
    public void clearSession(String sessionId) {
        PlannerAgent removed = sessions.remove(sessionId);
        java.util.List<Integer> caps = sessionCapabilities.remove(sessionId);
        sessionCapabilityInstances.remove(sessionId);  // Clear session-specific capability instances

        if (removed != null) {
            logger.info("🗑️  Clearing session: {} (had {} capabilities locked)", sessionId,
                       caps != null ? caps.size() : 0);

            // Generate usage report if there's any usage data
            try {
                if (usageManager.hasUsageData(sessionId)) {
                    String reportPath = usageManager.generateReport(sessionId);
                    if (reportPath != null) {
                        logger.info("📊 Usage report generated: {}", reportPath);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to generate usage report for session: {}", sessionId, e);
            } finally {
                // Clear usage data regardless of report generation success
                usageManager.clearSession(sessionId);
            }
        } else {
            logger.debug("Session {} was not found (already cleared)", sessionId);
        }
    }
    
    /**
     * Get all static capabilities (for UI display)
     * Note: This returns static capabilities without session-specific tracking
     */
    public Map<Integer, Capability> getCapabilities() {
        // Return a map that includes all possible capabilities (static + LLM-based)
        Map<Integer, Capability> allCaps = new HashMap<>(staticCapabilities);
        // Add placeholder entries for LLM-based capabilities (they'll be created per-session)
        allCaps.put(6, new ExecuteScriptCapability(baseModel));
        allCaps.put(100, new MathematicsCapability(baseModel));
        return allCaps;
    }

    /**
     * Check if there's usage data for a session
     */
    public boolean hasUsageData(String sessionId) {
        return usageManager.hasUsageData(sessionId);
    }

    /**
     * Generate usage report for a session without clearing it
     * Returns the path to the generated report, or null if no data
     */
    public String generateUsageReport(String sessionId) {
        try {
            if (usageManager.hasUsageData(sessionId)) {
                return usageManager.generateReport(sessionId);
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to generate usage report for session: {}", sessionId, e);
            return null;
        }
    }
}

