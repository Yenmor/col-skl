package com.skillhub.model;

/** 详情页返回：索引 + SKILL.md 全文 + persona 摘要 + 来源 */
public record SeniorSkillDetail(
        SeniorSkill index,
        String skillMd,
        String workMd,
        String personaMd,
        String manifestJson,
        String metaJson
) {
}
