package com.skillhub.dto;

/**
 * v1 创建评论请求。
 */
public record CreateCommentRequest(
        String body,
        String parentId
) {}