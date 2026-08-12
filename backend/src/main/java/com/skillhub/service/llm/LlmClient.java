package com.skillhub.service.llm;

import reactor.core.publisher.Mono;

public interface LlmClient {
    Mono<String> complete(String systemPrompt, String userMessage);
}
