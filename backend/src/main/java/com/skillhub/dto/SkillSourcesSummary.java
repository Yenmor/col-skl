package com.skillhub.dto;

import java.util.List;

public record SkillSourcesSummary(
        boolean available,
        int mappingCount,
        int threadCount,
        List<String> evidenceIds,
        String missingReason,
        String verification
) {
    public SkillSourcesSummary {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
