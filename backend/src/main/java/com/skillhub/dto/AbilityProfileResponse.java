package com.skillhub.dto;

import java.util.List;

/** Five-layer capability evidence maturity; legacy counters remain additive-compatible. */
public record AbilityProfileResponse(
        String userId,
        int total,
        String label,
        List<DomainScore> domains,
        LowestDirection lowestDirection,
        List<SkillRecallItem> recommendations
) {
    public record DomainScore(
            String id,
            String name,
            int score,
            int posts,
            int likes,
            int comments,
            int sitePosts,
            int seniors,
            List<BranchScore> branches,
            EvidenceCounts evidence
    ) {}

    public record BranchScore(
            String name,
            String note,
            int score,
            EvidenceCounts evidence
    ) {}

    public record EvidenceCounts(
            int posts,
            int comments,
            int receivedLikes,
            int receivedReplies,
            int privateDrafts,
            int publicSkills,
            int total
    ) {}

    public record LowestDirection(
            String domainId,
            String domainName,
            String branchName,
            int score,
            int evidenceCount
    ) {}
}
