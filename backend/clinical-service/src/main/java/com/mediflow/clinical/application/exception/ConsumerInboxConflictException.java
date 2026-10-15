package com.mediflow.clinical.application.exception;

/** A permanent event or operation identity collision; the broker must dead-letter it. */
public final class ConsumerInboxConflictException extends RuntimeException {
    public ConsumerInboxConflictException(String message) {
        super(message);
    }
}
