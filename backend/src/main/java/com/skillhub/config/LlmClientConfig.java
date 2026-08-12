package com.skillhub.config;

import com.skillhub.service.llm.DeepSeekLlmClient;
import com.skillhub.service.llm.LlmClient;
import com.skillhub.service.llm.MockLlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmClientConfig {
    @Bean
    @Primary
    public LlmClient activeLlmClient(LlmProperties properties,
                                     DeepSeekLlmClient deepSeek,
                                     MockLlmClient mock) {
        if ("deepseek".equalsIgnoreCase(properties.getProvider())) {
            return deepSeek;
        }
        return mock;
    }
}
