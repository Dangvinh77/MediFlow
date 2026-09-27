package com.mediflow.inpatient.infrastructure.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.api.ApiResponse.ErrorDetail;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.infrastructure.correlation.CorrelationIdRequestAttribute;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps domain and request failures to the shared API envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return fail(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({AdmissionRuleViolationException.class, BusinessRuleException.class})
    public ResponseEntity<ApiResponse<Void>> ruleViolation(
            RuntimeException exception, HttpServletRequest request) {
        String code = exception instanceof AdmissionRuleViolationException inpatient
                ? inpatient.code() : ((BusinessRuleException) exception).getCode();
        return fail(HttpStatus.UNPROCESSABLE_ENTITY, code, exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(
            AccessDeniedException exception, HttpServletRequest request) {
        return fail(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this operation", request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        String correlationId = correlationId(request);
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiError("VALIDATION_ERROR", "Request validation failed", details), correlationId));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(
            Exception exception, HttpServletRequest request) {
        return fail(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request contains an invalid or malformed value", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return fail(HttpStatus.CONFLICT, "INPATIENT_CONFLICT",
                "The requested change conflicts with current inpatient data", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled inpatient request failure", exception);
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, "INPATIENT_INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private static ResponseEntity<ApiResponse<Void>> fail(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.fail(
                ApiError.of(code, message), correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        var id = CorrelationIdRequestAttribute.read(request);
        return id == null ? null : id.toString();
    }
}
