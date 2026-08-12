package com.skillhub.model;

import java.time.Instant;

/**
 * 会话 v1（{@code docs/api-v1.md §5.2}）。
 */
public record ChatSession(
        String id,
        String userId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
