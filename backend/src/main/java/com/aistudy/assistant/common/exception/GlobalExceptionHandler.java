package com.aistudy.assistant.common.exception;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.filter.TraceIdFilter;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Centralized exception handler producing V2 {@link ApiResponse} envelopes with traceId and zero internal detail leakage.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[traceId={}] Application error [{}]: {}", traceId, code.name(), ex.getMessage(), ex);
        } else {
            log.warn("[traceId={}] Business error [{}]: {}", traceId, code.name(), ex.getMessage());
        }
        return buildErrorResponse(code, ex.getMessage(), ex.getFields(), traceId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        List<FieldErrorDetail> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return buildErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                fields,
                traceId
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        List<FieldErrorDetail> fields = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        return buildErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                fields,
                traceId
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.warn("[traceId={}] Malformed JSON payload: {}", traceId, ex.getMessage());
        return buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                "Dữ liệu JSON không đúng định dạng hoặc chứa giá trị không hợp lệ.",
                null,
                traceId
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        List<FieldErrorDetail> fields = List.of(new FieldErrorDetail(ex.getParameterName(), "Tham số bắt buộc bị thiếu."));
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "Thiếu tham số bắt buộc: " + ex.getParameterName(), fields, traceId);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        List<FieldErrorDetail> fields = List.of(new FieldErrorDetail(ex.getRequestPartName(), "Phần dữ liệu multipart bắt buộc bị thiếu."));
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "Thiếu trường dữ liệu multipart: " + ex.getRequestPartName(), fields, traceId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        List<FieldErrorDetail> fields = List.of(new FieldErrorDetail(ex.getName(), "Kiểu dữ liệu không hợp lệ."));
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "Giá trị tham số không hợp lệ: " + ex.getName(), fields, traceId);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.BAD_CREDENTIALS, ErrorCode.BAD_CREDENTIALS.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(
            LockedException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.ACCOUNT_LOCKED, ErrorCode.ACCOUNT_LOCKED.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(
            DisabledException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.ACCOUNT_DISABLED, ErrorCode.ACCOUNT_DISABLED.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.ENDPOINT_NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        return buildErrorResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            Exception ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.warn("[traceId={}] Optimistic lock conflict: {}", traceId, ex.getMessage());
        return buildErrorResponse(ErrorCode.VERSION_CONFLICT, ErrorCode.VERSION_CONFLICT.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.warn("[traceId={}] Data integrity constraint violated", traceId, ex);
        return buildErrorResponse(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getDefaultMessage(), null, traceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.error("[traceId={}] Unhandled internal server error", traceId, ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null, traceId);
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
            ErrorCode code,
            String message,
            List<FieldErrorDetail> fields,
            String traceId) {
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code, message, fields, traceId));
    }

    private FieldErrorDetail toFieldError(FieldError error) {
        String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Giá trị không hợp lệ";
        return FieldErrorDetail.builder()
                .field(error.getField())
                .message(message)
                .build();
    }

    private FieldErrorDetail toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "request";
        return FieldErrorDetail.builder()
                .field(path)
                .message(violation.getMessage())
                .build();
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = TraceIdFilter.currentTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String headerTraceId = request != null ? request.getHeader(TraceIdFilter.TRACE_ID_HEADER) : null;
        if (headerTraceId != null && !headerTraceId.isBlank()) {
            return headerTraceId;
        }
        return TraceIdFilter.generateTraceId();
    }
}
