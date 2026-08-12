package com.skillhub.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillhub.config.LlmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class DeepSeekLlmClient implements LlmClient {
    private final WebClient webClient;
    private final LlmProperties properties;
    private final ObjectMapper json;

    public DeepSeekLlmClient(WebClient.Builder builder, LlmProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.webClient = builder
            .baseUrl(properties.getDeepseek().getBaseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public Mono<String> complete(String systemPrompt, String userMessage) {
        String apiKey = properties.getDeepseek().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("DEEPSEEK_API_KEY 未配置"));
        }

        ObjectNode body = json.createObjectNode();
        body.put("model", properties.getDeepseek().getModel());
        body.put("temperature", 0.7);
        body.put("max_tokens", properties.getDeepseek().getMaxTokens());
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);

        return webClient.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
            .map(response -> {
                JsonNode content = response.path("choices").path(0).path("message").path("content");
                if (!content.isTextual() || content.asText().isBlank()) {
                    throw new IllegalStateException("DeepSeek 返回了空回答");
                }
                return content.asText();
            });
    }
}
