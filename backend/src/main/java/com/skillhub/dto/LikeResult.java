package com.skillhub.dto;

/**
 * 点赞结果（v1）。用于 {@code POST/DELETE /api/v1/posts/:postId/like}。
 *
 * <p>字段语义见 {@code docs/api-v1.md §4}。
 *
 * <p>计数策略（D3）：{@code post_likes(user_id, post_id)} 事实表 +
 * {@code posts.like_count} 冗余字段，**应用层事务**维护。
 *
 * @param likeCount 当前帖子的点赞总数
 * @param liked     当前用户是否已点赞（toggle 后的真值）
 */
public record LikeResult(int likeCount, boolean liked) {
}
