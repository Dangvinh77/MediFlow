package com.mediflow.<service>.infrastructure.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.api.ApiResponse.ErrorDetail;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Maps exceptions to the shared error envelope (docs/ai/05-api-conventions.md).
 *
 * <p>Domain exceptions extend the {@code common} bases, so catching those three covers every
 * business failure without this class needing to know the domain's exception names.
 *
 * <p>Copy into {@code <service>-service/web/} — only the package line changes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /** Covers every domain rule exception (they extend BusinessRuleException). */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        ApiError error = new ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Lỗi không mong đợi", ex);
        // Never leak a stack trace to the client.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống");
    }

    private ErrorDetail toDetail(FieldError fe) {
        return new ErrorDetail(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(ApiError.of(code, message)));
    }
}
