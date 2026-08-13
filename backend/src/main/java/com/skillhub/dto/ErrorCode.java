package com.skillhub.dto;

import org.springframework.http.HttpStatus;

/**
 * 错误码公共枚举（v1）。
 *
 * <p>位置决策（D9 + 方案一）：单一枚举，不分包。前端 TS 端以镜像枚举形式存在
 * （{@code frontend/src/types/api-v1.ts}）。
 *
 * <p>新增 code 流程：
 * <ol>
 *   <li>在 {@code docs/error-codes.md} 登记</li>
 *   <li>在本枚举添加常量</li>
 *   <li>在 {@code GlobalExceptionHandler} 添加映射（如需）</li>
 * </ol>
 *
 * <p>命名规范：{@code <RESOURCE>_<REASON>}，UPPER_SNAKE_CASE。
 */
public enum ErrorCode {

    // ---- AUTH_* ----
    AUTH_MISSING_USER_ID(HttpStatus.UNAUTHORIZED, "AUTH_MISSING_USER_ID", "X-User-Id 缺失或非法"),
    AUTH_INVALID_USER_ID(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_USER_ID", "X-User-Id 格式校验失败"),

    // ---- USER_* ----
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"),
    USER_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "USER_VALIDATION_FAILED", "用户字段校验失败"),
    USER_DISPLAY_NAME_TAKEN(HttpStatus.BAD_REQUEST, "USER_DISPLAY_NAME_TAKEN", "昵称已被占用"),

    // ---- POST_* ----
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在"),
    POST_TITLE_TOO_LONG(HttpStatus.BAD_REQUEST, "POST_TITLE_TOO_LONG", "标题超过 80 字"),
    POST_BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "POST_BODY_TOO_LONG", "正文超过 20000 字"),
    POST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "POST_VALIDATION_FAILED", "帖子参数校验失败"),

    // ---- COMMENT_* ----
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在"),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMENT_FORBIDDEN", "无权限操作该评论"),
    COMMENT_BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "COMMENT_BODY_TOO_LONG", "评论正文超过 2000 字"),
    COMMENT_PARENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "COMMENT_PARENT_NOT_FOUND", "父评论不存在"),
    COMMENT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMENT_VALIDATION_FAILED", "评论参数校验失败"),

    // ---- LIKE_* ----
    LIKE_ALREADY(HttpStatus.BAD_REQUEST, "LIKE_ALREADY", "重复点赞（应被幂等 toggle 吸收）"),
    LIKE_NOT_LIKED(HttpStatus.BAD_REQUEST, "LIKE_NOT_LIKED", "未点赞即取消（应被幂等 toggle 吸收）"),

    // ---- CHAT_* ----
    CHAT_EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "CHAT_EMPTY_MESSAGE", "消息为空"),
    CHAT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "CHAT_VALIDATION_FAILED", "聊天参数校验失败"),
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在"),
    CHAT_LLM_DEGRADED(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_LLM_DEGRADED", "LLM 全失败且 fallback 关闭"),

    // ---- SKILL_* ----
    SKILL_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "SKILL_VALIDATION_FAILED", "召回参数校验失败"),
    SKILL_RECALL_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "SKILL_RECALL_TIMEOUT", "召回超时"),
    SKILL_RECALL_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "SKILL_RECALL_DISABLED", "召回服务被关闭"),
    SKILL_NOT_FOUND(HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Skill 不存在"),
    SKILL_FORBIDDEN(HttpStatus.FORBIDDEN, "SKILL_FORBIDDEN", "无权访问该 Skill"),
    SKILL_IMPORT_INVALID(HttpStatus.BAD_REQUEST, "SKILL_IMPORT_INVALID", "Skill 包不合法"),
    SKILL_CONFLICT(HttpStatus.CONFLICT, "SKILL_CONFLICT", "Skill ID 已存在"),

    // ---- DISTILL_* ----
    DISTILL_LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DISTILL_LLM_UNAVAILABLE", "真实 LLM 未配置"),
    DISTILL_INSUFFICIENT_EVIDENCE(HttpStatus.UNPROCESSABLE_ENTITY, "DISTILL_INSUFFICIENT_EVIDENCE", "独立讨论线程不足"),
    DISTILL_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DISTILL_GENERATION_FAILED", "Skill 草稿生成失败"),

    // ---- SENIOR_* ----
    SENIOR_NOT_FOUND(HttpStatus.NOT_FOUND, "SENIOR_NOT_FOUND", "学长不存在"),
    SENIOR_DISTILL_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "SENIOR_DISTILL_TIMEOUT", "蒸馏超时"),

    // ---- GENERAL_* ----
    GENERAL_INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "GENERAL_INTERNAL", "服务器内部错误"),
    GENERAL_NOT_FOUND(HttpStatus.NOT_FOUND, "GENERAL_NOT_FOUND", "路由不存在"),
    GENERAL_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "GENERAL_METHOD_NOT_ALLOWED", "HTTP 方法不支持"),
    GENERAL_VALIDATION(HttpStatus.BAD_REQUEST, "GENERAL_VALIDATION", "全局参数校验失败");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
