package com.krista.kme.agent.usage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service to fetch and cache LLM pricing
 *
 * Strategy:
 * 1. If PORTKEY_API_KEY environment variable is set, use Portkey Models API
 * 2. Otherwise, load pricing from local JSON file (model-pricing.json in project root)
 * 3. Cache all pricing in memory to avoid repeated lookups
 *
 * Portkey API: https://api.portkey.ai/model-configs/pricing/{provider}/{model}
 * Prices are in cents per token (not dollars!)
 */
public class ModelPricingService {

    private static final Logger logger = LoggerFactory.getLogger(ModelPricingService.class);

    private static final String PORTKEY_API_BASE = "https://api.portkey.ai/model-configs/pricing";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String PRICING_FILE_PATH = "model-pricing.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ModelPricing> pricingCache;
    private final boolean usePortkeyAPI;
    private final Map<String, Map<String, JsonNode>> localPricingData;

    public ModelPricingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
        this.pricingCache = new ConcurrentHashMap<>();

        // Check if Portkey API key is available
        String apiKey = System.getenv("PORTKEY_API_KEY");
        this.usePortkeyAPI = apiKey != null && !apiKey.trim().isEmpty();

        if (usePortkeyAPI) {
            logger.info("✅ Using Portkey Models API for pricing (API key found)");
            this.localPricingData = null;
        } else {
            logger.info("📁 Using local pricing file: {}", PRICING_FILE_PATH);
            this.localPricingData = loadLocalPricingFile();
        }
    }
    
    /**
     * Get pricing for a model. Returns cached pricing if available.
     *
     * @param modelName Full model name (e.g., "gpt-4o-mini", "claude-3-5-sonnet-20241022")
     * @return ModelPricing object with input/output costs, or fallback pricing if lookup fails
     */
    public ModelPricing getPricing(String modelName) {
        // Check cache first
        if (pricingCache.containsKey(modelName)) {
            return pricingCache.get(modelName);
        }

        ModelPricing pricing;

        if (usePortkeyAPI) {
            // Use Portkey API
            pricing = fetchFromPortkeyAPI(modelName);
        } else {
            // Use local pricing file
            pricing = fetchFromLocalFile(modelName);
        }

        // Cache and return
        pricingCache.put(modelName, pricing);
        return pricing;
    }

    /**
     * Fetch pricing from Portkey API
     */
    private ModelPricing fetchFromPortkeyAPI(String modelName) {
        String provider = detectProvider(modelName);

        try {
            ModelPricing pricing = fetchPricingFromAPI(provider, modelName);
            logger.info("✅ Fetched pricing for {}: ${}/1M input, ${}/1M output",
                       modelName, pricing.inputCostPer1M, pricing.outputCostPer1M);
            return pricing;
        } catch (Exception e) {
            logger.warn("⚠️ Failed to fetch pricing for {} from Portkey API: {}. Using fallback.",
                       modelName, e.getMessage());
            return getFallbackPricing(modelName);
        }
    }

    /**
     * Fetch pricing from local JSON file
     */
    private ModelPricing fetchFromLocalFile(String modelName) {
        if (localPricingData == null) {
            logger.warn("⚠️ Local pricing data not loaded. Using fallback for {}", modelName);
            return getFallbackPricing(modelName);
        }

        String provider = detectProvider(modelName);

        try {
            Map<String, JsonNode> providerData = localPricingData.get(provider);
            if (providerData == null) {
                logger.warn("⚠️ Provider {} not found in local pricing file. Using fallback for {}",
                           provider, modelName);
                return getFallbackPricing(modelName);
            }

            JsonNode modelNode = providerData.get(modelName);
            if (modelNode == null) {
                logger.warn("⚠️ Model {} not found in local pricing file. Using fallback.", modelName);
                return getFallbackPricing(modelName);
            }

            JsonNode pricingConfig = modelNode.get("pricing_config");
            JsonNode payAsYouGo = pricingConfig.get("pay_as_you_go");

            // Extract prices (in cents per token)
            double inputPrice = payAsYouGo.get("request_token").get("price").asDouble();
            double outputPrice = payAsYouGo.get("response_token").get("price").asDouble();

            // Convert from cents per token to dollars per 1M tokens
            double inputCostPer1M = inputPrice * 10000;
            double outputCostPer1M = outputPrice * 10000;

            logger.debug("📁 Loaded pricing for {} from local file: ${}/1M input, ${}/1M output",
                        modelName, inputCostPer1M, outputCostPer1M);

            return new ModelPricing(inputCostPer1M, outputCostPer1M, provider);
        } catch (Exception e) {
            logger.warn("⚠️ Error reading pricing for {} from local file: {}. Using fallback.",
                       modelName, e.getMessage());
            return getFallbackPricing(modelName);
        }
    }
    
    /**
     * Fetch pricing from Portkey Models API
     */
    private ModelPricing fetchPricingFromAPI(String provider, String modelName) throws IOException, InterruptedException {
        String url = String.format("%s/%s/%s", PORTKEY_API_BASE, provider, modelName);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("API returned status " + response.statusCode());
        }
        
        // Parse JSON response
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode modelNode = root.get(modelName);
        
        if (modelNode == null) {
            throw new IOException("Model not found in response");
        }
        
        JsonNode pricingConfig = modelNode.get("pricing_config");
        JsonNode payAsYouGo = pricingConfig.get("pay_as_you_go");
        
        // Extract prices (in cents per token)
        double inputPrice = payAsYouGo.get("request_token").get("price").asDouble();
        double outputPrice = payAsYouGo.get("response_token").get("price").asDouble();
        
        // Convert from cents per token to dollars per 1M tokens
        double inputCostPer1M = inputPrice * 10000;  // cents/token * 10000 = $/1M tokens
        double outputCostPer1M = outputPrice * 10000;
        
        return new ModelPricing(inputCostPer1M, outputCostPer1M, provider);
    }
    
    /**
     * Load pricing data from local JSON file
     */
    private Map<String, Map<String, JsonNode>> loadLocalPricingFile() {
        Map<String, Map<String, JsonNode>> data = new ConcurrentHashMap<>();

        try {
            // Try to load from project root first
            Path projectRoot = Paths.get("").toAbsolutePath();
            Path pricingFilePath = projectRoot.resolve(PRICING_FILE_PATH);

            JsonNode root;
            if (Files.exists(pricingFilePath)) {
                logger.info("📁 Loading pricing from: {}", pricingFilePath);
                root = objectMapper.readTree(pricingFilePath.toFile());
            } else {
                // Try to load from classpath as fallback
                logger.info("📁 Pricing file not found at {}, trying classpath...", pricingFilePath);
                InputStream is = getClass().getClassLoader().getResourceAsStream(PRICING_FILE_PATH);
                if (is == null) {
                    logger.warn("⚠️ Pricing file not found in project root or classpath. Will use fallback pricing.");
                    return data;
                }
                root = objectMapper.readTree(is);
            }

            // Parse the JSON structure: { "provider": { "model-name": { ... } } }
            root.fields().forEachRemaining(providerEntry -> {
                String provider = providerEntry.getKey();
                JsonNode providerNode = providerEntry.getValue();

                Map<String, JsonNode> models = new ConcurrentHashMap<>();
                providerNode.fields().forEachRemaining(modelEntry -> {
                    String modelName = modelEntry.getKey();
                    JsonNode modelData = modelEntry.getValue();
                    models.put(modelName, modelData);
                });

                data.put(provider, models);
                logger.debug("📁 Loaded {} models for provider: {}", models.size(), provider);
            });

            logger.info("✅ Successfully loaded pricing for {} providers from local file", data.size());

        } catch (IOException e) {
            logger.error("❌ Failed to load local pricing file: {}", e.getMessage());
        }

        return data;
    }

    /**
     * Detect provider from model name
     */
    private String detectProvider(String modelName) {
        String lower = modelName.toLowerCase();

        if (lower.contains("gpt") || lower.contains("o1") || lower.contains("o3")) {
            return "openai";
        } else if (lower.contains("claude")) {
            return "anthropic";
        } else if (lower.contains("gemini")) {
            return "google";
        } else if (lower.contains("mistral")) {
            return "mistral-ai";
        } else if (lower.contains("llama")) {
            return "together-ai";
        }

        // Default to openai
        return "openai";
    }
    
    /**
     * Fallback pricing when API is unavailable
     */
    private ModelPricing getFallbackPricing(String modelName) {
        String lower = modelName.toLowerCase();
        
        // OpenAI models
        if (lower.contains("gpt-4o-mini")) {
            return new ModelPricing(0.150, 0.600, "openai");
        } else if (lower.contains("gpt-4o")) {
            return new ModelPricing(2.50, 10.00, "openai");
        }
        
        // Claude models
        else if (lower.contains("claude-3-5-sonnet")) {
            return new ModelPricing(3.00, 15.00, "anthropic");
        } else if (lower.contains("claude-3-haiku")) {
            return new ModelPricing(0.25, 1.25, "anthropic");
        }
        
        // Gemini models
        else if (lower.contains("gemini-1.5-pro")) {
            return new ModelPricing(1.25, 5.00, "google");
        } else if (lower.contains("gemini-1.5-flash")) {
            return new ModelPricing(0.075, 0.30, "google");
        }
        
        // Default fallback (gpt-4o-mini pricing)
        logger.warn("Using default fallback pricing for unknown model: {}", modelName);
        return new ModelPricing(0.150, 0.600, "unknown");
    }
    
    /**
     * Model pricing data
     */
    public static class ModelPricing {
        public final double inputCostPer1M;   // Cost per 1M input tokens in USD
        public final double outputCostPer1M;  // Cost per 1M output tokens in USD
        public final String provider;
        
        public ModelPricing(double inputCostPer1M, double outputCostPer1M, String provider) {
            this.inputCostPer1M = inputCostPer1M;
            this.outputCostPer1M = outputCostPer1M;
            this.provider = provider;
        }
    }
}

