package com.mediflow.report.web;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.api.ApiResponse.ErrorDetail;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.report.application.exception.ReportDateRangeException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/** Maps report web and business failures to the common response envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReportDateRangeException.class)
    public ResponseEntity<ApiResponse<Void>> dateRange(ReportDateRangeException exception,
                                                       HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException exception,
                                                           HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception,
                                                        HttpServletRequest request) {
        List<ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        return validationResponse(details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> methodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        List<ErrorDetail> details = exception.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorDetail(fieldName(result), message(error))))
                .toList();
        return validationResponse(details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraintValidation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<ErrorDetail> details = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorDetail(lastPathSegment(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return validationResponse(details, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException exception,
                                                          HttpServletRequest request) {
        return validationResponse(List.of(new ErrorDetail(exception.getName(),
                "Giá trị không đúng định dạng")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> missingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return validationResponse(List.of(new ErrorDetail(exception.getParameterName(),
                "Tham số là bắt buộc")), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformedRequest(HttpMessageNotReadableException exception,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request không đúng định dạng", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception,
                                                         HttpServletRequest request) {
        log.error("Lỗi không mong đợi trong report-service", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống", request);
    }

    private ErrorDetail toDetail(FieldError error) {
        return new ErrorDetail(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(List<ErrorDetail> details,
                                                                 HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details),
                correlationId(request)));
    }

    private static String fieldName(ParameterValidationResult result) {
        RequestParam requestParam = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) {
                return requestParam.name();
            }
            if (!requestParam.value().isBlank()) {
                return requestParam.value();
            }
        }
        return lastPathSegment(Objects.requireNonNullElse(
                result.getMethodParameter().getParameterName(), "request"));
    }

    private static String lastPathSegment(String path) {
        int separator = path.lastIndexOf('.');
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static String message(MessageSourceResolvable error) {
        return Objects.requireNonNullElse(error.getDefaultMessage(), "Dữ liệu không hợp lệ");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.fail(
                ApiError.of(code, message), correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        return request.getHeader(com.mediflow.common.security.JwtClaims.HEADER_CORRELATION_ID);
    }
}
