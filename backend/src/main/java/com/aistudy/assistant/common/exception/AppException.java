package com.aistudy.assistant.common.exception;

import com.aistudy.assistant.common.dto.FieldErrorDetail;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Standard business runtime exception carrying a structured {@link ErrorCode} and optional field errors.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldErrorDetail> fields;
    private final Long retryAfterSeconds;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.fields = Collections.emptyList();
        this.retryAfterSeconds = null;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fields = Collections.emptyList();
        this.retryAfterSeconds = null;
    }

    public AppException(ErrorCode errorCode, String message, Long retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.fields = Collections.emptyList();
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public AppException(ErrorCode errorCode, String message, List<FieldErrorDetail> fields) {
        super(message);
        this.errorCode = errorCode;
        this.fields = fields != null ? List.copyOf(fields) : Collections.emptyList();
        this.retryAfterSeconds = null;
    }

    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.fields = Collections.emptyList();
        this.retryAfterSeconds = null;
    }
}
