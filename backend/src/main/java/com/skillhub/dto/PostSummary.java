package com.skillhub.dto;

import java.time.Instant;

/**
 * 帖子摘要（v1）。用于列表与创建响应。
 *
 * <p>字段语义与约束见 {@code docs/api-v1.md §2.1, §2.2}。
 *
 * @param id           UUIDv4
 * @param title        1-80 字
 * @param excerpt      自动截前 200 字
 * @param coverColor   调色板 hex；后端按 id hash 选色
 * @param authorId     FK → users.id
 * @param authorName   冗余字段，避免前端二次查询
 * @param authorAvatar 冗余字段
 * @param domain       领域（竞赛 / 保研 / 科研 / 求职 / 实习 等）
 * @param likeCount    冗余计数，由 {@code post_likes} 事实表 + 应用层事务维护
 * @param commentCount 冗余计数
 * @param createdAt    ISO-8601 UTC
 */
public record PostSummary(
        String id,
        String title,
        String excerpt,
        String coverColor,
        String authorId,
        String authorName,
        String authorAvatar,
        String domain,
        int likeCount,
        int commentCount,
        Instant createdAt) {
}
