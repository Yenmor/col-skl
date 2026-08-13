package com.skillhub.config;

import com.skillhub.dto.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** A status exception that preserves the stable v1 error code and optional details. */
public class ApiException extends ResponseStatusException {
    private final ErrorCode errorCode;
    private final Object details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Object details) {
        super(errorCode.httpStatus(), message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ApiException(HttpStatusCode status, ErrorCode errorCode, String message, Object details) {
        super(status, message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object details() {
        return details;
    }
}
