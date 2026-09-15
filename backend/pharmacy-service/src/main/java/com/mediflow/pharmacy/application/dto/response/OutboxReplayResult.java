package com.mediflow.pharmacy.application.dto.response;

import java.util.UUID;

/**
 * Result returned after an administrator requeues a quarantined outbox event.
 *
 * @param eventId immutable identity retained for idempotent redelivery
 * @param replayed whether the event was found and made immediately retryable
 */
public record OutboxReplayResult(UUID eventId, boolean replayed) {
}
