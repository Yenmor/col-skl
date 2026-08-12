package com.skillhub.model;

import java.time.Instant;

/**
 * 记忆 v1（{@code docs/api-v1.md §6.2}）。
 */
public record ChatMemory(
        String id,
        String sessionId,
        String userId,
        String title,
        String tagsJson,
        String contentJson,
        Instant createdAt
) {
}
