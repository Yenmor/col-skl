package com.skillhub.dto;

import java.time.Instant;
import java.util.List;

/**
 * 蒸馏结果 DTO（v1）。用于 {@code POST /api/v1/seniors/:id/distill} 响应。
 *
 * <p>字段语义见 {@code docs/api-v1.md §8.1}。
 *
 * <p>同步返回（D14）。失败时 {@code fragments=[]}，前端空态展示，不破坏响应。
 *
 * @param seniorId  UUIDv4
 * @param fragments 蒸馏片段列表；空列表允许
 * @param updatedAt ISO-8601 UTC；服务端写入时的时间戳
 */
public record DistillResult(
        String seniorId,
        List<SeniorFragmentDto> fragments,
        Instant updatedAt) {
}
