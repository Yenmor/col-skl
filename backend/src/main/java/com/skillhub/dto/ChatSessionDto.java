package com.skillhub.dto;

import java.time.Instant;

/**
 * 会话摘要 DTO（v1）。用于 {@code GET /api/v1/chat/sessions} 列表。
 *
 * <p>字段语义见 {@code docs/api-v1.md §5.2}。
 *
 * @param sessionId UUIDv4
 * @param title     自动生成（可空）
 * @param updatedAt 最近一条消息的时间
 */
public record ChatSessionDto(String sessionId, String title, Instant updatedAt) {
}
