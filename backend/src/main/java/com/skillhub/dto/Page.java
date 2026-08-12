package com.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 列表分页响应（v1，cursor 分页）。
 *
 * <p>{@code cursor} 是 base64({{"id":"<lastItemId>"}})；客户端不解析，只回传。
 * 首屏请求可不带 {@code cursor}。{@code nextCursor=null} 表示已到末页。
 *
 * <p>所有列表接口（{@code /api/v1/posts}, {@code /api/v1/comments},
 * {@code /api/v1/chat/sessions}, {@code /api/v1/chat/sessions/:id/messages},
 * {@code /api/v1/users/me/memories}, {@code /api/v1/seniors/:id/fragments}）
 * 统一使用此结构。
 *
 * @param items      当前页数据
 * @param nextCursor 下一页 cursor；末页为 {@code null}
 * @param <T>        列表元素类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Page<T>(List<T> items, String nextCursor) {

    /**
     * 末页工厂。
     */
    public static <T> Page<T> tail(List<T> items) {
        return new Page<>(items, null);
    }
}
