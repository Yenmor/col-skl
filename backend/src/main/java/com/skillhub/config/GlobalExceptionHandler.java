package com.skillhub.config;

import com.skillhub.dto.ErrorCode;
import com.skillhub.dto.ErrorEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全局异常处理器（v1）。
 *
 * <p>所有 4xx / 5xx 响应统一为 {@link ErrorEnvelope} 形式，详见
 * {@code docs/api-v1.md §0.4} 与 {@code docs/error-codes.md}。
 *
 * <p>本类<b>只</b>做异常→{@code ErrorEnvelope} 的映射，<b>不</b>处理业务异常。
 * 业务层应通过 {@link ResponseStatusException} 携带 {@link ErrorCode#code()}
 * 抛错。
 *
 * <p>{@code traceId} 处理：
 * <ol>
 *   <li>优先读请求头 {@code X-Trace-Id}</li>
 *   <li>否则服务端生成 UUIDv4</li>
 *   <li>写 MDC（与 Logback 配合）</li>
 *   <li>写入响应头 {@code X-Trace-Id} 与 {@link ErrorEnvelope.ErrorBody#traceId()}</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    /**
     * 业务层抛的 {@code ResponseStatusException}（推荐：业务层用此方式携带
     * {@link ErrorCode}）。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorEnvelope>> handleResponseStatus(
            ResponseStatusException ex,
            ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        if (ex instanceof ApiException api) {
            return build(status, api.errorCode(), message, api.details(), exchange);
        }
        return build(status, mapStatusToCode(status), message, null, exchange);
    }

    /**
     * 全局参数校验失败（{@code @Valid} 抛出的）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<ErrorEnvelope>> handleValidation(
            MethodArgumentNotValidException ex,
            ServerWebExchange exchange) {
        Object details = ex.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.GENERAL_VALIDATION, "参数校验失败", details, exchange);
    }

    /**
     * 业务层抛的 {@code IllegalArgumentException}（兜底）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorEnvelope>> handleIllegalArgument(
            IllegalArgumentException ex,
            ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.GENERAL_VALIDATION, ex.getMessage(), null, exchange);
    }

    /**
     * 兜底。任何未捕获异常。
     */
    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ErrorEnvelope>> handleAny(
            Throwable ex,
            ServerWebExchange exchange) {
        LOG.error("unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.GENERAL_INTERNAL,
                "服务器内部错误", null, exchange);
    }

    // --- helpers ---

    private static Mono<ResponseEntity<ErrorEnvelope>> build(
            HttpStatus status, ErrorCode code, String message, Object details, ServerWebExchange exchange) {
        return build(status, code.code(), message, details, exchange);
    }

    private static Mono<ResponseEntity<ErrorEnvelope>> build(
            HttpStatus status, String code, String message, Object details, ServerWebExchange exchange) {
        String traceId = resolveOrCreateTraceId(exchange);
        MDC.put(MDC_TRACE_KEY, traceId);
        try {
            ErrorEnvelope envelope = ErrorEnvelope.of(code, message, details, traceId);
            exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);
            return Mono.just(ResponseEntity.status(status).body(envelope));
        } finally {
            MDC.remove(MDC_TRACE_KEY);
        }
    }

    private static String resolveOrCreateTraceId(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    private static String mapStatusToCode(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return ErrorCode.GENERAL_NOT_FOUND.code();
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            return ErrorCode.GENERAL_METHOD_NOT_ALLOWED.code();
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return ErrorCode.GENERAL_VALIDATION.code();
        }
        if (status.is5xxServerError()) {
            return ErrorCode.GENERAL_INTERNAL.code();
        }
        return ErrorCode.GENERAL_VALIDATION.code();
    }
}
