package com.skillhub.dto;

import java.time.Instant;

/**
 * 用户 DTO（v1）。对应 {@code GET /api/v1/users/me} 与 {@code PATCH} 响应。
 *
 * <p>字段语义与约束见 {@code docs/api-v1.md §1}。
 *
 * @param id          UUIDv4
 * @param displayName 1-24 字符
 * @param avatarUrl   可空
 * @param role        角色；MVP 仅 {@code GUEST}
 * @param createdAt   ISO-8601 UTC
 */
public record UserDto(
        String id,
        String displayName,
        String avatarUrl,
        String role,
        Instant createdAt) {
}
