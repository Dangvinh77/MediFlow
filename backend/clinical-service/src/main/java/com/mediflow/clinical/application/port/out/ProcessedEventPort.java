package com.mediflow.clinical.application.port.out;

import java.util.UUID;

/** BR-X2: deduplication and the attachment must commit atomically; adapter must handle concurrent redelivery. */
public interface ProcessedEventPort {
    /**
     * Atomically claims an event for processing.
     *
     * @return {@code true} only when this call inserted the event marker
     */
    boolean tryClaim(UUID eventId, String eventType);
}
