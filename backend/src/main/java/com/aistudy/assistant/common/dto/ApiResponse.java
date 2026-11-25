package com.aistudy.assistant.common.dto;

import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.filter.TraceIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Unified V2 API response envelope containing status, payload, pagination metadata, error details, and traceId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private PageMeta meta;
    private ErrorDetail error;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        return success(data, null, TraceIdFilter.currentTraceId());
    }

    public static <T> ApiResponse<T> success(T data, PageMeta meta) {
        return success(data, meta, TraceIdFilter.currentTraceId());
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return success(data, null, traceId);
    }

    public static <T> ApiResponse<T> success(T data, PageMeta meta, String traceId) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(meta)
                .traceId(traceId)
                .build();
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, null, TraceIdFilter.currentTraceId());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message, String traceId) {
        return error(errorCode, message, null, traceId);
    }

    public static ApiResponse<Void> error(
            ErrorCode errorCode,
            String message,
            List<FieldErrorDetail> fields,
            String traceId) {
        List<FieldErrorDetail> sanitizedFields = (fields == null || fields.isEmpty()) ? null : fields;
        return ApiResponse.<Void>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(errorCode.name())
                        .message(message)
                        .fields(sanitizedFields)
                        .build())
                .traceId(traceId)
                .build();
    }
}
