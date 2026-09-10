package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** Idempotency boundary for event consumers; the adapter must share the transaction with effects. */
public interface ProcessedEventPort {

    boolean alreadyProcessed(UUID eventId);

    void markProcessed(UUID eventId, String routingKey);
}
