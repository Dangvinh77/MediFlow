package com.mediflow.report.messaging.consumer;

/** Poison event that cannot be safely mapped to a report use case. */
public class ReportEventValidationException extends RuntimeException {

    public ReportEventValidationException(String message) {
        super(message);
    }

    public ReportEventValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
