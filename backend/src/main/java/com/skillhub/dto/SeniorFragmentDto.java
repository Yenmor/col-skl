package com.skillhub.dto;

import java.time.Instant;
import java.util.List;

/**
 * 蒸馏片段 DTO（v1）。用于 {@code POST /api/v1/seniors/:id/distill} 响应，
 * 与 {@code GET /api/v1/seniors/:id/fragments} 列表元素。
 *
 * <p>字段语义见 {@code docs/api-v1.md §8.1, §8.2}。
 *
 * <p>{@code kind} 枚举见 {@link SeniorFragmentKind}（D17）：
 * {@code PERSONA / WORK / MEMORY / OTHER}。
 *
 * <p>Java 端只透传外壳字段。复杂结构由 Python 端负责。
 */
public record SeniorFragmentDto(
        String id,
        String seniorId,
        SeniorFragmentKind kind,
        String content,
        List<String> tags,
        Instant createdAt) {

    /**
     * 片段种类枚举（D17）。
     */
    public enum SeniorFragmentKind {
        /** 人格 / 性格 / 表达风格 */
        PERSONA,
        /** 履历 / 工作 / 成就 */
        WORK,
        /** 记忆 / 经历 / 故事 */
        MEMORY,
        /** 其它兜底 */
        OTHER
    }
}
