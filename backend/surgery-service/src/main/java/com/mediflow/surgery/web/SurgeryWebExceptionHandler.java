package com.mediflow.surgery.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;

import java.util.List;
import java.util.UUID;

/** Keeps cancellation validation and domain failures in the shared API envelope. */
@RestControllerAdvice
public class SurgeryWebExceptionHandler {

    @ExceptionHandler(SurgeryRevisionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> revisionConflict(
            SurgeryRevisionConflictException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "SURGERY_REVISION_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(
            BusinessRuleException exception, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiResponse.ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(SurgeryWebExceptionHandler::toDetail).toList();
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiResponse.ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details),
                correlationId(request)));
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> malformedRequest(Exception exception,
                                                                HttpServletRequest request) {
        String message = exception instanceof MissingRequestHeaderException missing
                ? "Thiếu header bắt buộc: " + missing.getHeaderName()
                : "Request không đúng định dạng";
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> methodValidation(Exception exception,
                                                               HttpServletRequest request) {
        List<ApiResponse.ErrorDetail> details;
        if (exception instanceof HandlerMethodValidationException validation) {
            details = validation.getAllValidationResults().stream()
                    .flatMap(result -> result.getResolvableErrors().stream().map(error ->
                            new ApiResponse.ErrorDetail(result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())))
                    .toList();
        } else {
            ConstraintViolationException validation = (ConstraintViolationException) exception;
            details = validation.getConstraintViolations().stream()
                    .map(violation -> new ApiResponse.ErrorDetail(
                            violation.getPropertyPath().toString(), violation.getMessage()))
                    .toList();
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiResponse.ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details),
                correlationId(request)));
    }

    private static ApiResponse.ErrorDetail toDetail(FieldError error) {
        return new ApiResponse.ErrorDetail(error.getField(), error.getDefaultMessage());
    }

    private static ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code,
                                                            String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.fail(
                ApiResponse.ApiError.of(code, message), correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        UUID correlationId = CorrelationIdRequestAttribute.read(request);
        return correlationId == null ? UUID.randomUUID().toString() : correlationId.toString();
    }
}
