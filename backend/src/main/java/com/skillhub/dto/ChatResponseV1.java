package com.skillhub.dto;

import java.util.List;

/**
 * 聊天响应 DTO（v1）。对应 {@code POST /api/v1/chat}。
 *
 * <p>字段语义见 {@code docs/api-v1.md §5.1}。
 *
 * @param sessionId 服务端生成 / 回传（D5）
 * @param answers   多学长回答（前端不假定长度；当前由 ChatOrchestrator 决定）
 */
public record ChatResponseV1(String sessionId, List<Answer> answers) {

    /**
     * 单个学长回答。{@code content} 是自然语言，可能含 markdown。
     */
    public record Answer(
            String seniorId,
            String name,
            String school,
            String major,
            String year,
            String content) {
    }
}
