package com.skillhub.dto;

import java.time.Instant;
import java.util.List;

/**
 * 记忆 DTO（v1）。用于 {@code GET /api/v1/users/me/memories} 列表。
 *
 * <p>字段语义见 {@code docs/api-v1.md §6.2}。
 *
 * <p>由 {@code POST /api/v1/chat/sessions/:id/memories} 蒸馏产生。
 *
 * @param memoryId  UUIDv4
 * @param sessionId 来源会话
 * @param title     可空
 * @param tags      分类器（事项 10）产出
 * @param createdAt ISO-8601 UTC
 */
public record ChatMemoryDto(
        String memoryId,
        String sessionId,
        String title,
        List<String> tags,
        Instant createdAt) {
}
