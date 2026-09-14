package com.mediflow.pharmacy.application.port.out;

/**
 * Classifies retryable infrastructure failures without exposing framework exception types to the
 * application layer.
 */
@FunctionalInterface
public interface TransientFailureClassifierPort {

    /**
     * Returns whether the failure should abort the current batch and be retried later.
     *
     * @param exception failure raised by an outbound adapter
     * @return {@code true} for a transient infrastructure failure
     */
    boolean isTransient(RuntimeException exception);
}
