package com.skillhub.model;

import java.time.Instant;
import java.util.List;

/**
 * 学长.Skill 的最小索引 — 不存 SKILL.md 全文（那在文件系统里），
 * 只存用于列表 / 过滤 / 详情页的元数据。
 */
public record SeniorSkill(
        String id,
        String name,
        String school,
        String major,
        String year,
        String domain,         // 保研 / 竞赛 / 科研 / 求职 / 实习
        String avatarFilename, // 文件名，加载自 seniors/<id>/<avatarFilename>
        String source,         // "manual" | "distilled"
        Instant createdAt,
        String ownerId,
        String visibility,     // PUBLIC | PRIVATE
        String layerId,
        String summary,
        String version,
        List<String> tags,
        Instant updatedAt
) {
    public static final String PUBLIC = "PUBLIC";
    public static final String PRIVATE = "PRIVATE";

    /** Source-compatible constructor for legacy callers and seeded bundles. */
    public SeniorSkill(String id, String name, String school, String major, String year,
                       String domain, String avatarFilename, String source, Instant createdAt) {
        this(id, name, school, major, year, domain, avatarFilename, source, createdAt,
            null, PUBLIC, null, null, "v1", List.of(), createdAt);
    }

    public SeniorSkill {
        visibility = PRIVATE.equalsIgnoreCase(visibility) ? PRIVATE : PUBLIC;
        tags = tags == null ? List.of() : List.copyOf(tags);
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public boolean isPublic() {
        return PUBLIC.equals(visibility);
    }

    public boolean isOwnedBy(String userId) {
        return userId != null && userId.equals(ownerId);
    }
}
