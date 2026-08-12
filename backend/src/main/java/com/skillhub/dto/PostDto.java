package com.skillhub.dto;

import java.time.Instant;

/**
 * 帖子 DTO 工厂 / 别名（v1）。
 *
 * <p>列表与创建响应统一使用 {@link PostSummary}；详情使用 {@link PostDetail}。
 * 字段语义见 {@code docs/api-v1.md §2}。
 */
public final class PostDto {
    private PostDto() {}
}
