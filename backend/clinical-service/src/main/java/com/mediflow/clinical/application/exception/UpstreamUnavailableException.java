package com.mediflow.clinical.application.exception;

/** Integration failure, distinct from a business lookup miss. Web adapter must map to HTTP 503. */
public class UpstreamUnavailableException extends RuntimeException {
    private final String code;

    public UpstreamUnavailableException(String message) {
        this("UPSTREAM_UNAVAILABLE", message, null);
    }

    public UpstreamUnavailableException(String message, Throwable cause) {
        this("UPSTREAM_UNAVAILABLE", message, cause);
    }

    public UpstreamUnavailableException(String code, String message) {
        this(code, message, null);
    }

    public UpstreamUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
