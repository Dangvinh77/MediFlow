package com.mediflow.common.exception;

/** Thrown when a domain/business rule is violated. Maps to HTTP 422 (Unprocessable Entity). */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
