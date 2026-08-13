package com.skillhub.dto;

import com.skillhub.model.SeniorSkill;

import java.time.Instant;
import java.util.List;

public record SkillSummary(
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
        SkillTrust trust
) {
    public static SkillSummary from(SeniorSkill skill, SkillTrust trust) {
        return new SkillSummary(
            skill.id(), skill.name(), skill.school(), skill.major(), skill.year(),
            skill.domain(), skill.avatarFilename(), skill.source(), skill.ownerId(),
            skill.visibility(), skill.layerId(), skill.summary(), skill.version(),
            skill.tags(), skill.createdAt(), skill.updatedAt(), trust);
    }
}
