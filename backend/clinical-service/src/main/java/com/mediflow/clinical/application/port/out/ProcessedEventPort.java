package com.mediflow.clinical.application.port.out;

import java.util.UUID;

/** BR-X2: deduplication and the attachment must commit atomically; adapter must handle concurrent redelivery. */
public interface ProcessedEventPort {
    boolean alreadyProcessed(UUID eventId);
    void markProcessed(UUID eventId, String routingKey);
}
