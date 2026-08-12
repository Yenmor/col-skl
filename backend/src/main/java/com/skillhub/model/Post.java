package com.skillhub.model;

import java.time.Instant;

/**
 * 帖子 v1（{@code docs/api-v1.md §2}）。
 *
 * @param authorId     FK → users.id；nullable 兼容老 {@code community_posts} 行
 * @param authorName   冗余字段，避免前端二次查询
 * @param authorAvatar 冗余字段
 */
public record Post(
        String id,
        String title,
        String excerpt,
        String body,
        String coverColor,
        String authorId,
        String authorName,
        String authorAvatar,
        String domain,
        long likeCount,
        long commentCount,
        Instant createdAt
) {
}
