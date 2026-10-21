package com.mediflow.lab.application.exception;

/** A required upstream authority could not be verified, so the request must fail closed. */
public final class LabUpstreamUnavailableException extends RuntimeException {
    public LabUpstreamUnavailableException() {
        super("Required upstream authority is unavailable");
    }
}
