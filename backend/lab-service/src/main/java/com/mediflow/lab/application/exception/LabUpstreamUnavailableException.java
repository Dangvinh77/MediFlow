package com.mediflow.lab.application.exception;

/** Clinical source-order authority could not be verified, so the request must fail closed. */
public final class LabUpstreamUnavailableException extends RuntimeException {
    public LabUpstreamUnavailableException() {
        super("Clinical source-order authority is unavailable");
    }
}
