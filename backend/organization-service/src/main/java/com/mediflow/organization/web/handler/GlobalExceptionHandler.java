package com.mediflow.organization.web.handler;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.DomainException;
import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.exception.InvalidCredentialsException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.exception.StaffAlreadyInDepartmentException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Maps Organization errors to the shared response envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final CorrelationIdProvider correlationIds;

    public GlobalExceptionHandler(CorrelationIdProvider correlationIds) {
        this.correlationIds = correlationIds;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(
            DuplicateResourceException exception) {
        return buildResponse(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {
        List<ApiResponse.ErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiResponse.ErrorDetail(
                        error.getField(), error.getDefaultMessage()))
                .toList();
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                details);
    }

    @ExceptionHandler(DoctorLicenseRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleDoctorLicenseRequired(
            DoctorLicenseRequiredException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "DOCTOR_LICENSE_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(InvalidDepartmentHeadException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDepartmentHead(
            InvalidDepartmentHeadException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "DEPARTMENT_HEAD_INVALID", exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAccount(
            InvalidAccountException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "ACCOUNT_INVALID", exception.getMessage());
    }

    @ExceptionHandler(DepartmentHasActiveStaffException.class)
    public ResponseEntity<ApiResponse<Void>> handleDepartmentHasActiveStaff(
            DepartmentHasActiveStaffException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "DEPARTMENT_HAS_ACTIVE_STAFF", exception.getMessage());
    }

    @ExceptionHandler(DepartmentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDepartmentNotFound(
            DepartmentNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND,
                "DEPARTMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
            InvalidCredentialsException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "AUTH_INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFound(
            AccountNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(StaffNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStaffNotFound(
            StaffNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND,
                "STAFF_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DepartmentInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleDepartmentInactive(
            DepartmentInactiveException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "DEPARTMENT_INACTIVE", exception.getMessage());
    }

    @ExceptionHandler(StaffAlreadyInDepartmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleStaffAlreadyInDepartment(
            StaffAlreadyInDepartmentException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "STAFF_ALREADY_IN_DEPARTMENT", exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND,
                exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(
            DomainException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "BUSINESS_RULE_VIOLATION", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception) {
        return buildResponse(HttpStatus.FORBIDDEN,
                "FORBIDDEN", "You do not have permission to perform this operation");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception exception) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(
            HttpStatus status,
            String code,
            String message) {
        return buildResponse(status, code, message, List.of());
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(
            HttpStatus status,
            String code,
            String message,
            List<ApiResponse.ErrorDetail> details) {
        String correlationId = correlationIds.currentOrCreate().toString();
        ApiResponse.ApiError error = new ApiResponse.ApiError(code, message, details);
        return ResponseEntity.status(status)
                .header(JwtClaims.HEADER_CORRELATION_ID, correlationId)
                .body(ApiResponse.fail(error, correlationId));
    }
}
