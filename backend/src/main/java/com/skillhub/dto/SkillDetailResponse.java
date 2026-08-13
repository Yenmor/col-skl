package com.skillhub.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** Public v1 detail is deliberately flat; the legacy /api/seniors detail stays unchanged. */
public record SkillDetailResponse(
        String id,
        String name,
        String school,
        String major,
        String year,
        String domain,
        String avatarFilename,
        String source,
        String ownerId,
        String visibility,
        String layerId,
        String summary,
        String version,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt,
        SkillTrust trust,
        String skillMd,
        String workMd,
        String personaMd,
        JsonNode manifest,
        JsonNode meta,
        SkillSourcesSummary sources,
        SkillTrustEvidence trustEvidence
) {
    public static SkillDetailResponse from(SkillSummary s, String skillMd, String workMd,
                                           String personaMd, JsonNode manifest, JsonNode meta,
                                           SkillSourcesSummary sources) {
        return new SkillDetailResponse(
            s.id(), s.name(), s.school(), s.major(), s.year(), s.domain(),
            s.avatarFilename(), s.source(), s.ownerId(), s.visibility(), s.layerId(),
            s.summary(), s.version(), s.tags(), s.createdAt(), s.updatedAt(), s.trust(),
            skillMd, workMd, personaMd, manifest, meta, sources, null);
    }

    public SkillDetailResponse withTrustEvidence(SkillTrustEvidence evidence) {
        return new SkillDetailResponse(
            id, name, school, major, year, domain, avatarFilename, source, ownerId,
            visibility, layerId, summary, version, tags, createdAt, updatedAt, trust,
            skillMd, workMd, personaMd, manifest, meta, sources, evidence);
    }
}
