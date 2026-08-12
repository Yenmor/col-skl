package com.skillhub.dto;

import java.util.List;

/**
 * 技能召回条目（v1）。用于 {@code POST /api/v1/skills/recall} 响应。
 *
 * <p>字段语义见 {@code docs/api-v1.md §7.1}。
 *
 * <p>Java 端只透传外壳字段。复杂结构（嵌套对话、引用链、概率）由 Python 端
 * 负责，**Java 端不解析**。
 *
 * @param seniorId UUIDv4
 * @param score    0.0~1.0 浮点
 * @param text     自然语言片段；可能含 markdown
 * @param tags     分类器（事项 10）产出的标签
 */
public record SkillRecallItem(
        String seniorId,
        double score,
        String text,
        List<String> tags) {
}
