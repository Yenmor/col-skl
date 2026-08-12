package com.skillhub.dto;

/**
 * 聊天请求 DTO（v1）。对应 {@code POST /api/v1/chat}。
 *
 * <p><b>注意</b>：本类必须保持为可变 Java class（带 setter），<b>不能</b>改写为 record。
 * 原因：Spring WebFlux 对 {@code record} 类型的 request body 绑定不稳定，
 * 会出现 400 响应（与现有 {@code com.skillhub.dto.ChatRequest} 的约束一致，见
 * {@code CLAUDE.md} 与 {@code AGENTS.md} 运行时约束）。
 *
 * <p>字段语义见 {@code docs/api-v1.md §5.1}。
 */
public class ChatRequestV1 {

    /** 1-2000 字；非空。 */
    private String message;

    /** 可空；首请求不传，服务端补 UUIDv4 并在响应中回传（D5）。 */
    private String sessionId;

    public ChatRequestV1() {
    }

    public ChatRequestV1(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
