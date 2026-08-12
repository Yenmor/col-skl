package com.skillhub.dto;

/**
 * v1 创建帖子请求。
 */
public record CreatePostRequest(
        String title,
        String body,
        String domain
) {}