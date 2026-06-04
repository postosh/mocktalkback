package com.mocktalkback.global.exception;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.mocktalkback.global.common.dto.ApiEnvelope;
import com.mocktalkback.global.common.dto.ApiError;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import com.mocktalkback.global.i18n.ApiMessageResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String KEY_PARAM_INVALID = "error.param.invalid";
    private static final String KEY_HTTP_METHOD_NOT_SUPPORTED = "error.http_method_not_supported";
    private static final String KEY_REQUEST_BODY_MALFORMED = "error.request_body_malformed";
    private static final String KEY_UPLOAD_MAX_SIZE = "error.upload.max_size";

    private final ApiMessageResolver messageResolver;

    public GlobalExceptionHandler(ApiMessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        return buildError(ex.getErrorCode(), request, null, null, ex.getArgs());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = extractFieldErrorDetails(ex.getBindingResult());
        return buildError(ErrorCode.COMMON_BAD_REQUEST, request, null, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> details = extractConstraintViolationDetails(ex.getConstraintViolations());
        return buildError(ErrorCode.COMMON_BAD_REQUEST, request, null, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String reason = messageResolver.resolveKey(KEY_PARAM_INVALID, ex.getName());
        return buildError(ErrorCode.COMMON_BAD_REQUEST, request, reason, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String reason = messageResolver.resolveKey(KEY_REQUEST_BODY_MALFORMED);
        return buildError(ErrorCode.COMMON_BAD_REQUEST, request, reason, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String reason = messageResolver.resolveKey(KEY_HTTP_METHOD_NOT_SUPPORTED, ex.getMethod());
        return buildError(ErrorCode.COMMON_METHOD_NOT_ALLOWED, request, reason, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildError(ErrorCode.COMMON_UNAUTHORIZED, request, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildError(ErrorCode.COMMON_FORBIDDEN, request, null, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Unexpected IllegalArgumentException: path={}, message={}", request.getRequestURI(), ex.getMessage());
        return buildError(ErrorCode.COMMON_BAD_REQUEST, request, null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String reason = messageResolver.resolveKey(KEY_UPLOAD_MAX_SIZE, 50);
        return buildError(ErrorCode.COMMON_PAYLOAD_TOO_LARGE, request, reason, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String reasonKey = resolveDataIntegrityReasonKey(ex);
        String reason = messageResolver.resolveKey(reasonKey);
        return buildError(ErrorCode.COMMON_CONFLICT, request, reason, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return buildError(ErrorCode.COMMON_INTERNAL_ERROR, request, null, null);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        log.debug("Async request already closed: path={}, message={}", request.getRequestURI(), ex.getMessage());
    }

    private Map<String, Object> extractFieldErrorDetails(BindingResult result) {
        List<FieldError> errors = result.getFieldErrors();
        List<Map<String, String>> fieldErrors = errors.stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage()
                ))
                .collect(Collectors.toList());
        return Map.of("fieldErrors", fieldErrors);
    }

    private Map<String, Object> extractConstraintViolationDetails(Set<ConstraintViolation<?>> violations) {
        List<Map<String, String>> violationErrors = violations.stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()
                ))
                .collect(Collectors.toList());
        return Map.of("violations", violationErrors);
    }

    private ResponseEntity<ApiEnvelope<Void>> buildError(
            ErrorCode errorCode,
            HttpServletRequest request,
            String reason,
            Map<String, Object> details
    ) {
        return buildError(errorCode, request, reason, details, new Object[0]);
    }

    private ResponseEntity<ApiEnvelope<Void>> buildError(
            ErrorCode errorCode,
            HttpServletRequest request,
            String reason,
            Map<String, Object> details,
            Object[] args
    ) {
        String path = request != null ? request.getRequestURI() : "";
        ApiError error = messageResolver.toApiError(errorCode, reason, path, details, args);
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiEnvelope.fail(error));
    }

    private String resolveDataIntegrityReasonKey(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message == null) {
            return "error.duplicate.generic";
        }
        if (message.contains("uq_tb_boards_slug")) {
            return "error.duplicate.slug";
        }
        if (message.contains("uq_tb_boards_board_name")) {
            return "error.duplicate.board_name";
        }
        if (message.contains("uq_tb_users_login_id")) {
            return "error.duplicate.login_id";
        }
        if (message.contains("uq_tb_users_email")) {
            return "error.duplicate.email";
        }
        if (message.contains("uq_tb_users_handle")) {
            return "error.duplicate.handle";
        }
        return "error.duplicate.generic";
    }
}