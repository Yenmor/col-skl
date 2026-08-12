package com.skillhub.model;

import java.time.Instant;

/**
 * 点赞事实表条目（{@code docs/api-v1.md §4}）。
 * 应用层事务维护 {@code posts.like_count} 冗余字段。
 */
public record PostLike(
        String userId,
        String postId,
        Instant createdAt
) {
}
