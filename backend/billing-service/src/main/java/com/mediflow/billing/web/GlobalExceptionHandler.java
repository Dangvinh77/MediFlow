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

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ApiResponse<Void>> storageFailure(RuntimeException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        boolean lookup = request.getRequestURI().startsWith("/api/v1/billing/financial-clearances/");
        boolean ledger = request.getRequestURI().startsWith("/api/v1/billing/payment-requests/")
                || request.getRequestURI().startsWith("/api/v1/billing/transactions/")
                || request.getRequestURI().matches("/api/v1/billing/accounts/[^/]+/settlement-preview")
                || request.getRequestURI().matches("/api/v1/billing/accounts/[^/]+/settlement-reconciliations")
                || request.getRequestURI().matches("/api/v1/billing/accounts/[^/]+/deposit-allocations")
                || request.getRequestURI().matches("/api/v1/billing/accounts/[^/]+/final-settlements")
                || request.getRequestURI().startsWith("/api/v1/billing/prescription-adjustments/");
        if (!lookup && !ledger) return unexpected(ex, request);
        // Do not leak SQL, ledger values or connection details to this service contract.
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                lookup ? "BILLING_CLEARANCE_UNAVAILABLE" : "BILLING_LEDGER_UNAVAILABLE",
                lookup ? "Financial authority unavailable" : "Financial ledger unavailable; retry with the same operation key",
                request);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> missingHeader(org.springframework.web.bind.MissingRequestHeaderException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Required request header is missing", request);
    }

    /** 404 — {@code INVOICE_NOT_FOUND} / {@code FEE_NOT_FOUND}. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request);
    }

    /** 409 — an idempotency key cannot be reused for different payment intent or actor. */
    @ExceptionHandler(com.mediflow.common.exception.DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(com.mediflow.common.exception.DuplicateResourceException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request);
    }

    /** 422 — business invariants (BR-B1, BR-B2, BR-B8, BR-B9, ...). */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), request);
    }

    /** 400 — DTO request không qua Bean Validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        ApiError error = new ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details);
        String correlation = correlationId(request);
        return ResponseEntity.badRequest()
                .header(com.mediflow.common.security.JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.fail(error, correlation));
    }

    /** 400 — JSON hỏng, sai kiểu tham số. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> malformedRequest(Exception ex,
            jakarta.servlet.http.HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request không đúng định dạng", request);
    }

    /** 403 — đã xác thực nhưng không đủ role (Spring Security {@code @PreAuthorize}). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", request);
    }

    /** A contended transaction is unavailable, never a successful or fabricated financial result. */
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> freshSnapshotRequired(org.springframework.dao.ConcurrencyFailureException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        // The new finalizer owns this retry contract; preserve existing endpoint error codes.
        if (!request.getRequestURI().matches("/api/v1/billing/accounts/[^/]+/final-settlements")) {
            return storageFailure(ex, request);
        }
        return build(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_RETRY_FRESH_SNAPSHOT", "Retry the same operation key from a fresh snapshot", request);
    }

    /** 500 — mọi lỗi chưa xử lý riêng; không lộ stack trace ra ngoài. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex,
            jakarta.servlet.http.HttpServletRequest request) {
        log.error("Lỗi không mong đợi", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống", request);
    }

    private ErrorDetail toDetail(FieldError fe) {
        return new ErrorDetail(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message,
            jakarta.servlet.http.HttpServletRequest request) {
        String correlation = correlationId(request);
        return ResponseEntity.status(status)
                .header(com.mediflow.common.security.JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.fail(ApiError.of(code, message), correlation));
    }

    /** Always carries a correlation id on error responses, generating one if the caller sent none. */
    private static String correlationId(jakarta.servlet.http.HttpServletRequest request) {
        String supplied = request.getHeader(com.mediflow.common.security.JwtClaims.HEADER_CORRELATION_ID);
        return supplied == null || supplied.isBlank() ? java.util.UUID.randomUUID().toString() : supplied;
    }
}
