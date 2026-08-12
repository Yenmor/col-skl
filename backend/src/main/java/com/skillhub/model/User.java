package com.skillhub.model;

import java.time.Instant;

public record User(
        String id,
        String displayName,
        String avatarUrl,
        String role,
        Instant createdAt
) {
}
