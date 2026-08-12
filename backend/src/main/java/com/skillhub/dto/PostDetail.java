package com.skillhub.dto;

import java.time.Instant;

/**
 * 帖子详情（v1）。用于 {@code GET /api/v1/posts/:id}。
 *
 * <p>字段语义见 {@code docs/api-v1.md §2.3}。
 *
 * @param summary 帖子摘要字段
 * @param body    完整正文（1-20000 字）
 */
public record PostDetail(PostSummary summary, String body) {
}
