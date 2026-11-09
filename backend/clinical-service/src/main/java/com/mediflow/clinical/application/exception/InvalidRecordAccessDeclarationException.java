package com.mediflow.clinical.application.exception;

/** The stated purpose or reason does not satisfy the rules for the actor's role. */
public class InvalidRecordAccessDeclarationException extends RuntimeException {
    public InvalidRecordAccessDeclarationException(String message) {
        super(message);
    }
}
