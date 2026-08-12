package com.skillhub.model;

import java.time.Instant;

/**
 * 评论 v1（{@code docs/api-v1.md §3}）。
 */
public record Comment(
        String id,
        String postId,
        String authorId,
        String authorName,
        String authorAvatar,
        String parentId,
        String body,
        Instant createdAt
) {
}
