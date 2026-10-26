package com.mediflow.organization.application.exception;

public class SchedulingAuthorityUnavailableException extends RuntimeException {
    public SchedulingAuthorityUnavailableException(Throwable cause) {
        super("Scheduling authority is temporarily unavailable", cause);
    }
}
