package com.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.llm")
public class LlmProperties {
    private String provider = "mock";
    private int timeoutSeconds = 60;
    private boolean fallbackToMock;
    private DeepSeek deepseek = new DeepSeek();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public boolean isFallbackToMock() { return fallbackToMock; }
    public void setFallbackToMock(boolean fallbackToMock) { this.fallbackToMock = fallbackToMock; }
    public DeepSeek getDeepseek() { return deepseek; }
    public void setDeepseek(DeepSeek deepseek) { this.deepseek = deepseek; }

    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com/v1";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private int maxTokens = 900;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }
}
