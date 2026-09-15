package com.mediflow.organization.domain.exception;

/** Thrown when an account violates a domain business rule. */
public class InvalidAccountException
        extends DomainException {

    public InvalidAccountException(String message) {
        super(message);
    }
}
