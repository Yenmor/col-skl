package com.skillhub.service.llm;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MockLlmClient implements LlmClient {
    private static final Pattern NAME_PATTERN = Pattern.compile("「(.+?)」");

    @Override
    public Mono<String> complete(String systemPrompt, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("（本地示例回答）我是").append(extractName(systemPrompt))
          .append("，主要方向是").append(extractDomain(systemPrompt)).append("。")
          .append("你的问题「").append(userMessage).append("」，我的视角是：先拆成你能控制的几步，再结合我的经验给你一个判断。")
          .append("要让每位专家给出真实回答，请在 backend 配置 LLM_PROVIDER=deepseek 与 DEEPSEEK_API_KEY。");
        if (systemPrompt != null && systemPrompt.contains("【论坛社区讨论（参考）】")) {
            sb.append("\n我参考了社区里的相关讨论，仅作背景，供你判断。");
        }
        return Mono.just(sb.toString());
    }

    private static String extractName(String systemPrompt) {
        if (systemPrompt == null) return "本地专家";
        Matcher m = NAME_PATTERN.matcher(systemPrompt);
        return m.find() ? m.group(1) : "本地专家";
    }

    private static String extractDomain(String systemPrompt) {
        if (systemPrompt == null) return "未标注";
        int start = systemPrompt.indexOf("主要领域是");
        if (start < 0) return "未标注";
        start += "主要领域是".length();
        int end = systemPrompt.indexOf("。", start);
        if (end < 0) end = systemPrompt.length();
        String domain = systemPrompt.substring(start, end).trim();
        return domain.isEmpty() ? "未标注" : domain;
    }
}
