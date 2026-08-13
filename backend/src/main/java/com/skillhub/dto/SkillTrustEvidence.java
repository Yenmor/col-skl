package com.skillhub.dto;

/**
 * Explainable trust evidence. Package quality remains in {@link SkillTrust};
 * these signals answer whether the source was confirmed and how the Skill was adopted.
 */
public record SkillTrustEvidence(
        int overall,
        String level,
        String summary,
        Item source,
        Item platform,
        Item community
) {
    public record Item(
            String status,
            String label,
            String detail,
            Integer score,
            Boolean confirmed,
            Boolean authorized,
            Boolean aiAssisted,
            Integer aiScore,
            String authority,
            Integer authorityCount,
            Long likes,
            Long downloads,
            Long comments
    ) {}
}
