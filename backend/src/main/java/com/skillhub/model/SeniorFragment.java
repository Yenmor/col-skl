package com.skillhub.model;

import java.time.Instant;

/**
 * 蒸馏片段 v1（{@code docs/api-v1.md §8}）。
 */
public record SeniorFragment(
        String id,
        String seniorId,
        String kind,            // PERSONA | WORK | MEMORY | OTHER
        String content,
        String tagsJson,
        Instant createdAt
) {
}
