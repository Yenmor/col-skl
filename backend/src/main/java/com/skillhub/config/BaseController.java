package com.skillhub.config;

import com.skillhub.dto.ErrorCode;
import com.skillhub.dto.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * v1 Controller 公共基类。
 *
 * <p>提供 4 个工具方法：
 * <ul>
 *   <li>{@link #page(List, int)} —— 列表分页（cursor 由最后一项的 id 编码）</li>
 *   <li>{@link #currentUserId()} —— 读 {@code X-User-Id}；缺失或非 UUIDv4 抛 401</li>
 *   <li>{@link #newTraceId()} —— 兜底生成 traceId（一般由 {@code GlobalExceptionHandler} 处理）</li>
 *   <li>{@link #notFound(ErrorCode, String)} —— 抛带 code 的 {@code ResponseStatusException}</li>
 * </ul>
 *
 * <p>所有 v1 controller 应继承本类以获得统一行为。
 */
public abstract class BaseController {

    private static final String USER_HEADER = "X-User-Id";

    // --- cursor ---

    /**
     * 根据最后一项的 id 编码下一页 cursor；返回 null 表示已到末页。
     */
    protected String encodeCursor(String lastItemId) {
        if (lastItemId == null) {
            return null;
        }
        String raw = "{\"id\":\"" + lastItemId + "\"}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码 cursor 得到 lastItemId；非法时抛 400 {@code GENERAL_VALIDATION}。
     */
    protected String decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            // 极简 JSON 解析：信任格式 {"id":"..."}
            int i = raw.indexOf("\"id\"");
            if (i < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
            }
            int s = raw.indexOf('"', i + 5);
            int e = raw.indexOf('"', s + 1);
            if (s < 0 || e < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
            }
            return raw.substring(s + 1, e);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
        }
    }

    /**
     * 列表分页：若 {@code items.size() > limit}，截断 + 生成 {@code nextCursor}。
     */
    protected <T> Page<T> page(List<T> items, int limit) {
        if (items == null) {
            return Page.tail(List.of());
        }
        if (items.size() <= limit) {
            return Page.tail(items);
        }
        List<T> truncated = items.subList(0, limit);
        // 通过反射或类型断言取得 id 字段；若 T 没有 getId()，则简单用最后一项 toString
        Object last = truncated.get(truncated.size() - 1);
        String lastId = extractId(last);
        return new Page<>(truncated, encodeCursor(lastId));
    }

    private static String extractId(Object o) {
        if (o == null) {
            return null;
        }
        try {
            var m = o.getClass().getMethod("id");
            Object v = m.invoke(o);
            return v == null ? null : v.toString();
        } catch (ReflectiveOperationException ex) {
            return o.toString();
        }
    }

    // --- user ---

    /**
     * 读 {@code X-User-Id}；缺失或非 UUIDv4 抛 401 {@code AUTH_MISSING_USER_ID}。
     */
    protected String currentUserId() {
        String header = currentUserIdHeader();
        if (header == null) {
            throw unauthorized(ErrorCode.AUTH_MISSING_USER_ID, "X-User-Id 缺失");
        }
        try {
            UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            throw unauthorized(ErrorCode.AUTH_INVALID_USER_ID, "X-User-Id 非 UUIDv4");
        }
        return header;
    }

    /**
     * 仅返回 header 原文（不抛错）。供可选 userId 接口使用。
     */
    protected String currentUserIdHeader() {
        org.springframework.web.server.ServerWebExchange exchange = currentExchange();
        if (exchange == null) {
            return null;
        }
        return exchange.getRequest().getHeaders().getFirst(USER_HEADER);
    }

    // --- trace ---

    /**
     * 生成新的 traceId（UUIDv4）。一般由 {@code GlobalExceptionHandler} 处理，
     * 本方法作为逃生口。
     */
    protected String newTraceId() {
        return UUID.randomUUID().toString();
    }

    // --- error helpers ---

    /**
     * 抛 404 + 指定 code。
     */
    protected ResponseStatusException notFound(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    /**
     * 抛 401 + 指定 code。
     */
    protected ResponseStatusException unauthorized(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    /**
     * 抛 400 + 指定 code。
     */
    protected ResponseStatusException badRequest(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    /**
     * 抛 503 + 指定 code。
     */
    protected ResponseStatusException serviceUnavailable(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    /**
     * 当前请求的 {@code ServerWebExchange}（由 Spring 注入；本类不强制注入，
     * 子类可在方法参数上声明）。
     */
    protected org.springframework.web.server.ServerWebExchange currentExchange() {
        return ExchangeHolder.get();
    }

    /**
     * 内部工具：在 controller 方法执行前将 {@code ServerWebExchange} 暂存到
     * ThreadLocal，方法结束后清除。{@code BaseController} 子类可重写 controller
     * 方法签名直接以参数方式获取 exchange（推荐）。
     */
    protected static final class ExchangeHolder {
        private static final ThreadLocal<org.springframework.web.server.ServerWebExchange> CURRENT =
                new ThreadLocal<>();

        static void set(org.springframework.web.server.ServerWebExchange exchange) {
            CURRENT.set(exchange);
        }

        static org.springframework.web.server.ServerWebExchange get() {
            return CURRENT.get();
        }

        static void clear() {
            CURRENT.remove();
        }
    }
}
