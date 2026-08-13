package com.skillhub.service.llm;

import reactor.core.publisher.Mono;

public interface LlmClient {
    Mono<String> complete(String systemPrompt, String userMessage);

    /**
     * 按调用场景限定 max_tokens 与超时：聊天场景短回答用小的 token 上限与较短超时，
     * 避免模型生成长文本把请求拖满全局超时；沉淀等长输出场景继续用全局配置（走两参方法）。
     */
    default Mono<String> complete(String systemPrompt, String userMessage,
                                  int maxTokens, int timeoutSeconds) {
        return complete(systemPrompt, userMessage);
    }
}
