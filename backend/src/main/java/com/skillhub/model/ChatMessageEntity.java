package com.skillhub.model;

import java.time.Instant;

/**
 * 聊天记录持久化的最小单元。answers 字段是 JSON 数组，
 * 写库时序列化，读时反序列化。
 */
public record ChatMessageEntity(
        String id,
        String sessionId,
        String role,           // "user" | "assistant"
        String content,        // 用户消息或会话语义聚合
        String answersJson,    // 仅 assistant 有：序列化后的 [{seniorId,name,...}]
        Instant createdAt
) {
}
