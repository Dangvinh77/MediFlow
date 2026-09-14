package com.mediflow.billing.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.api.ApiResponse.ErrorDetail;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ResourceNotFoundException;

/**
 * Chuyển exception ở tầng web thành {@link ApiResponse} theo chuẩn API chung
 * (docs/ai/05-api-conventions.md). Domain exception của billing đều kế thừa hai lớp cơ sở của
 * {@code common} ({@code BillingRuleException} → {@link BusinessRuleException},
 * {@code InvoiceNotFoundException}/{@code FeeNotFoundException} → {@link ResourceNotFoundException}),
 * nên bắt hai lớp cơ sở này là đủ, không cần biết tên từng exception cụ thể.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 — {@code INVOICE_NOT_FOUND} / {@code FEE_NOT_FOUND}. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    /** 422 — mọi mã {@code BILLING_*} (BR-B1, BR-B2, BR-B8, BR-B9, ...). */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage());
    }

    /** 400 — DTO request không qua Bean Validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        ApiError error = new ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    /** 400 — JSON hỏng, sai kiểu tham số. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> malformedRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request không đúng định dạng");
    }

    /** 403 — đã xác thực nhưng không đủ role (Spring Security {@code @PreAuthorize}). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");
    }

    /** 500 — mọi lỗi chưa xử lý riêng; không lộ stack trace ra ngoài. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Lỗi không mong đợi", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống");
    }

    private ErrorDetail toDetail(FieldError fe) {
        return new ErrorDetail(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(ApiError.of(code, message)));
    }
}
