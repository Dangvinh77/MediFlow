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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SurgeryWebExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "This operation is not permitted", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        String correlation = correlationId(request);
        if (exception instanceof org.springframework.web.ErrorResponse framework
                && framework.getStatusCode().is4xxClientError()) {
            String code = switch (framework.getStatusCode().value()) {
                case 400 -> "INVALID_REQUEST";
                case 404 -> "NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                default -> "HTTP_ERROR";
            };
            return ResponseEntity.status(framework.getStatusCode()).body(ApiResponse.fail(
                    ApiResponse.ApiError.of(code, "Request cannot be processed"), correlation));
        }
        // Do not print exception messages, SQL, clinical input, tokens or payloads.
        LOGGER.error("Surgery request failed correlationId={} exceptionType={}",
                correlation, exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(
                ApiResponse.ApiError.of("INTERNAL_ERROR", "The operation could not be completed"), correlation));
    }

    @ExceptionHandler(com.mediflow.surgery.application.exception.UpstreamUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> upstreamUnavailable(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SURGERY_UPSTREAM_UNAVAILABLE",
                "Authoritative dependency is temporarily unavailable", request);
    }

    @ExceptionHandler(com.mediflow.surgery.application.exception.SurgeryReadAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> readDenied(
            com.mediflow.surgery.application.exception.SurgeryReadAccessDeniedException exception,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidParameter(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Query or path parameter is invalid", request);
    }

    @ExceptionHandler(SurgeryRevisionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> revisionConflict(
            SurgeryRevisionConflictException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "SURGERY_REVISION_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(com.mediflow.surgery.application.exception.SurgeryCommandBusyException.class)
    public ResponseEntity<ApiResponse<Void>> commandBusy(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "SURGERY_COMMAND_BUSY", "Resource is busy; retry the same command later", request);
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
