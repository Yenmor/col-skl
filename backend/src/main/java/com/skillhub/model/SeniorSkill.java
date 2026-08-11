package com.skillhub.model;

import java.time.Instant;

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
        Instant createdAt
) {
}
