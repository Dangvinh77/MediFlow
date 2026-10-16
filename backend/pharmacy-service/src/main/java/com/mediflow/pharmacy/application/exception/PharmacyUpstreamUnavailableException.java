package com.mediflow.pharmacy.application.exception;

/** Unavailable authority is not a negative clinical decision or a stock failure. */
public final class PharmacyUpstreamUnavailableException extends RuntimeException {
    public PharmacyUpstreamUnavailableException() {
        super("Required Pharmacy upstream authority is unavailable");
    }
}
