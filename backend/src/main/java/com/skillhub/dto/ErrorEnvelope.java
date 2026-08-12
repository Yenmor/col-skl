package com.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一错误响应外壳（v1）。
 *
 * <p>所有 4xx / 5xx 响应必须包成此结构。详见 {@code docs/api-v1.md §0.4} 与
 * {@code docs/error-codes.md}。
 *
 * <p>Java 字段命名遵守现有 Spring Boot Jackson 配置（camelCase），由全局
 * {@code WebConfig} 负责 JSON 序列化。
 *
 * @param error 错误体；非空
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(ErrorBody error) {

    /**
     * 错误体。详见 {@code docs/error-codes.md}。
     *
     * @param code    资源前缀 + 下划线 + 原因，如 {@code POST_NOT_FOUND}
     * @param message 给人看的本地化文案
     * @param details 可选上下文（如出错的 id 列表）
     * @param traceId 全链路追踪 ID；后端在缺省时填入 UUIDv4
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            Object details,
            String traceId) {
    }

    /**
     * 工厂方法。
     *
     * @param code    枚举 code
     * @param message 文案
     * @param traceId 追踪 ID（可空；空时由 GlobalExceptionHandler 注入）
     * @return 完整外壳
     */
    public static ErrorEnvelope of(String code, String message, String traceId) {
        return new ErrorEnvelope(new ErrorBody(code, message, null, traceId));
    }

    /**
     * 工厂方法（带 details）。
     */
    public static ErrorEnvelope of(String code, String message, Object details, String traceId) {
        return new ErrorEnvelope(new ErrorBody(code, message, details, traceId));
    }
}
