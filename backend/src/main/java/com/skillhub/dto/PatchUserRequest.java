package com.skillhub.dto;

/**
 * v1 补丁用户请求。
 */
public record PatchUserRequest(
        String displayName,
        String avatarUrl
) {}