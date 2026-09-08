package com.mediflow.clinical.application.exception;

/** Integration failure, distinct from a business lookup miss. Web adapter must map to HTTP 503. */
public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String message) {
        super(message);
    }

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getCode() { return "UPSTREAM_UNAVAILABLE"; }
}
