package com.skillhub.dto;

import java.time.Instant;

/**
 * 评论 DTO（v1）。用于列表与创建响应。
 *
 * <p>字段语义见 {@code docs/api-v1.md §3}。
 *
 * <p>v1 UI 展平一层；{@code parentId} 字段保留，**不渲染楼中楼**。
 *
 * @param id           UUIDv4
 * @param postId       FK → posts.id
 * @param authorId     FK → users.id
 * @param authorName   冗余字段
 * @param authorAvatar 冗余字段
 * @param parentId     FK → comments.id；顶层评论为 {@code null}
 * @param body         1-2000 字
 * @param createdAt    ISO-8601 UTC
 */
public record CommentDto(
        String id,
        String postId,
        String authorId,
        String authorName,
        String authorAvatar,
        String parentId,
        String body,
        Instant createdAt) {
}
