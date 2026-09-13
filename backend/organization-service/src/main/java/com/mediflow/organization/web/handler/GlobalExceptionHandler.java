package com.mediflow.organization.web.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException;
import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.exception.StaffAlreadyInDepartmentException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(
                        error.getField(),
                        error.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation failed");
        body.put("message", "Request validation failed");
        body.put("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    @ExceptionHandler(DoctorLicenseRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleDoctorLicenseRequired(
            DoctorLicenseRequiredException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Doctor license is required",
                exception.getMessage());
    }

    @ExceptionHandler(InvalidDepartmentHeadException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDepartmentHead(
            InvalidDepartmentHeadException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid department head",
                exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAccount(
            InvalidAccountException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid account",
                exception.getMessage());
    }

    @ExceptionHandler(DepartmentHasActiveStaffException.class)
    public ResponseEntity<Map<String, Object>> handleDepartmentHasActiveStaff(
            DepartmentHasActiveStaffException exception) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Department has active staff",
                exception.getMessage());
    }

    @ExceptionHandler(DepartmentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDepartmentNotFound(
            DepartmentNotFoundException exception) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Department not found",
                exception.getMessage());
    }

    @ExceptionHandler(StaffNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStaffNotFound(
            StaffNotFoundException exception) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Staff not found",
                exception.getMessage());
    }

    @ExceptionHandler(DepartmentInactiveException.class)
    public ResponseEntity<Map<String, Object>> handleDepartmentInactive(
            DepartmentInactiveException exception) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Department is inactive",
                exception.getMessage());
    }

    @ExceptionHandler(StaffAlreadyInDepartmentException.class)
    public ResponseEntity<Map<String, Object>> handleStaffAlreadyInDepartment(
            StaffAlreadyInDepartmentException exception) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Staff already belongs to department",
                exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String error,
            String message) {

        Map<String, Object> body = new HashMap<>();

        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);

        return ResponseEntity
                .status(status)
                .body(body);
    }
}