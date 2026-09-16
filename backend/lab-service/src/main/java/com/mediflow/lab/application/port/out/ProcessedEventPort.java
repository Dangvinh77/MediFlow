package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** Idempotency boundary for event consumers; the adapter must share the transaction with effects. */
public interface ProcessedEventPort {

    /**
     * Atomically claims an event for processing.
     *
     * @return {@code true} only when this call inserted the event marker
     */
    boolean tryClaim(UUID eventId, String eventType);
}
