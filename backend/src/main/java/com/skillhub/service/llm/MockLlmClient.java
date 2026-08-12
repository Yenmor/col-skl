package com.skillhub.service.llm;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MockLlmClient implements LlmClient {
    @Override
    public Mono<String> complete(String systemPrompt, String userMessage) {
        return Mono.just("暂时使用本地示例回答。请在 backend 中配置 LLM_PROVIDER=deepseek 和 DEEPSEEK_API_KEY 后启用真实 AI。\n\n你的问题：「" + userMessage + "」");
    }
}
