package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

/** Defines the operator action for requeuing a durable pharmacy integration event. */
public interface ReplayPharmacyOutboxUseCase {

    /**
     * Requeues an existing outbox event while preserving its identity and payload.
     *
     * @param eventId immutable event identifier
     * @return {@code true} when the event exists and was requeued; {@code false} otherwise
     */
    boolean replay(UUID eventId);
}
