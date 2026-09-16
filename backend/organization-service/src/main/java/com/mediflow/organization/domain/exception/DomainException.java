package com.mediflow.organization.domain.exception;

/** Base exception for domain business-rule violations. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
