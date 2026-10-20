package com.mediflow.report.application.exception;

public class OutpatientCoverageConflictException extends RuntimeException {
    public OutpatientCoverageConflictException() {
        super("Coverage inputs changed, are invalid, or the request ID was already used differently");
    }
}
