package com.skillhub.dto;

import java.time.Instant;

/**
 * 聊天消息 DTO（v1）。用于 {@code GET /api/v1/chat/sessions/:sessionId/messages}。
 *
 * <p>字段语义见 {@code docs/api-v1.md §5.3}。
 *
 * <p>{@code answers} 在 user 行为 {@code null}；在 assistant 行为是
 * {@code ChatResponseV1.answers} 的 JSON 序列化产物（透传原文）。
 *
 * @param role      {@code user} 或 {@code assistant}
 * @param content   用户消息原文 / assistant 内容
 * @param answers   assistant 行的答案 JSON 序列化
 * @param createdAt ISO-8601 UTC
 */
public record ChatMessageDto(
        String role,
        String content,
        Object answers,
        Instant createdAt) {
}
