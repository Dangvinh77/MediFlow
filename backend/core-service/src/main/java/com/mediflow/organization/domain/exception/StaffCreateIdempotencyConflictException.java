package com.mediflow.organization.domain.exception;

public class StaffCreateIdempotencyConflictException extends RuntimeException {
    public StaffCreateIdempotencyConflictException() {
        super("Idempotency key has already been used for a different staff request");
    }
}
