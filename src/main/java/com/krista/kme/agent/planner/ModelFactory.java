package com.krista.kme.agent.planner;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Factory for creating ChatLanguageModel instances from different providers.
 * 
 * Supports:
 * - OpenAI (GPT-4, GPT-4o, GPT-3.5-turbo, etc.)
 * - Anthropic Claude (Claude 3.5 Sonnet, Claude 3 Opus, etc.)
 * - Google Gemini (gemini-pro, gemini-1.5-pro, etc.)
 * - Azure OpenAI
 */
public class ModelFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelFactory.class);
    
    /**
     * Supported LLM providers
     */
    public enum Provider {
        OPENAI,
        ANTHROPIC,
        GOOGLE_GEMINI,
        AZURE_OPENAI
    }
    
    /**
     * Configuration for creating a model
     */
    public static class ModelConfig {
        private final Provider provider;
        private final String modelName;
        private final String apiKey;
        private final Double temperature;
        private final Integer timeoutSeconds;
        private final String azureEndpoint;  // Only for Azure
        private final String azureDeploymentName;  // Only for Azure
        
        private ModelConfig(Builder builder) {
            this.provider = builder.provider;
            this.modelName = builder.modelName;
            this.apiKey = builder.apiKey;
            this.temperature = builder.temperature;
            this.timeoutSeconds = builder.timeoutSeconds;
            this.azureEndpoint = builder.azureEndpoint;
            this.azureDeploymentName = builder.azureDeploymentName;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private Provider provider = Provider.OPENAI;
            private String modelName = "gpt-4o-mini";
            private String apiKey;
            private Double temperature = 0.3;
            private Integer timeoutSeconds = 60;
            private String azureEndpoint;
            private String azureDeploymentName;
            
            public Builder provider(Provider provider) {
                this.provider = provider;
                return this;
            }
            
            public Builder modelName(String modelName) {
                this.modelName = modelName;
                return this;
            }
            
            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }
            
            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }
            
            public Builder timeoutSeconds(Integer timeoutSeconds) {
                this.timeoutSeconds = timeoutSeconds;
                return this;
            }
            
            public Builder azureEndpoint(String azureEndpoint) {
                this.azureEndpoint = azureEndpoint;
                return this;
            }
            
            public Builder azureDeploymentName(String azureDeploymentName) {
                this.azureDeploymentName = azureDeploymentName;
                return this;
            }
            
            public ModelConfig build() {
                return new ModelConfig(this);
            }
        }
        
        public Provider getProvider() { return provider; }
        public String getModelName() { return modelName; }
        public String getApiKey() { return apiKey; }
        public Double getTemperature() { return temperature; }
        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public String getAzureEndpoint() { return azureEndpoint; }
        public String getAzureDeploymentName() { return azureDeploymentName; }
    }
    
    /**
     * Create a ChatLanguageModel based on the configuration
     */
    public static ChatLanguageModel createModel(ModelConfig config) {
        logger.info("Creating {} model: {}", config.getProvider(), config.getModelName());

        switch (config.getProvider()) {
            case OPENAI:
                return createOpenAiModel(config);
            case ANTHROPIC:
                return createAnthropicModel(config);
            case GOOGLE_GEMINI:
                return createGoogleGeminiModel(config);
            case AZURE_OPENAI:
                return createAzureOpenAiModel(config);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + config.getProvider());
        }
    }

    /**
     * Create OpenAI model (GPT-4, GPT-4o, GPT-3.5-turbo, etc.)
     */
    private static ChatLanguageModel createOpenAiModel(ModelConfig config) {
        String apiKey = config.getApiKey() != null ? config.getApiKey() : System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not provided. Set OPENAI_API_KEY environment variable.");
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * Create Anthropic Claude model (Claude 3.5 Sonnet, Claude 3 Opus, etc.)
     */
    private static ChatLanguageModel createAnthropicModel(ModelConfig config) {
        String apiKey = config.getApiKey() != null ? config.getApiKey() : System.getenv("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Anthropic API key not provided. Set ANTHROPIC_API_KEY environment variable.");
        }

        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * Create Google Gemini model (gemini-pro, gemini-1.5-pro, etc.)
     */
    private static ChatLanguageModel createGoogleGeminiModel(ModelConfig config) {
        String apiKey = config.getApiKey() != null ? config.getApiKey() : System.getenv("GOOGLE_AI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Google AI API key not provided. Set GOOGLE_AI_API_KEY environment variable.");
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequestsAndResponses(false)
                .build();
    }

    /**
     * Create Azure OpenAI model
     */
    private static ChatLanguageModel createAzureOpenAiModel(ModelConfig config) {
        String apiKey = config.getApiKey() != null ? config.getApiKey() : System.getenv("AZURE_OPENAI_API_KEY");
        String endpoint = config.getAzureEndpoint() != null ? config.getAzureEndpoint() : System.getenv("AZURE_OPENAI_ENDPOINT");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Azure OpenAI API key not provided. Set AZURE_OPENAI_API_KEY environment variable.");
        }

        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalStateException("Azure OpenAI endpoint not provided. Set AZURE_OPENAI_ENDPOINT environment variable.");
        }

        return AzureOpenAiChatModel.builder()
                .apiKey(apiKey)
                .endpoint(endpoint)
                .deploymentName(config.getAzureDeploymentName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequestsAndResponses(false)
                .build();
    }

    /**
     * Convenience method to create a model from environment variables
     * Reads LLM_PROVIDER and LLM_MODEL_NAME from environment
     */
    public static ChatLanguageModel createFromEnvironment() {
        String providerStr = System.getenv("LLM_PROVIDER");
        String modelName = System.getenv("LLM_MODEL_NAME");

        Provider provider = Provider.OPENAI;  // Default
        if (providerStr != null && !providerStr.isEmpty()) {
            try {
                provider = Provider.valueOf(providerStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid LLM_PROVIDER: {}. Using default: OPENAI", providerStr);
            }
        }

        // Set default model names based on provider
        if (modelName == null || modelName.isEmpty()) {
            switch (provider) {
                case OPENAI:
                    modelName = "gpt-4o-mini";
                    break;
                case ANTHROPIC:
                    modelName = "claude-3-5-sonnet-20241022";
                    break;
                case GOOGLE_GEMINI:
                    modelName = "gemini-1.5-pro";
                    break;
                case AZURE_OPENAI:
                    modelName = "gpt-4o-mini";
                    break;
            }
        }

        logger.info("Creating model from environment: provider={}, model={}", provider, modelName);

        return createModel(ModelConfig.builder()
                .provider(provider)
                .modelName(modelName)
                .build());
    }
}
