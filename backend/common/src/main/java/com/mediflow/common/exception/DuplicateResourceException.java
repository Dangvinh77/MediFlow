package com.mediflow.common.exception;

/** Thrown when creating a resource that violates a uniqueness rule. Maps to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    private final String code;

    public DuplicateResourceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
