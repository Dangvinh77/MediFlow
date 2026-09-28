package com.mediflow.surgery.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores approved serialized event bytes atomically with the owning case command. */
public interface SurgeryOutboxPort {

    void append(OutgoingEvent event);

    Optional<Delivery> claimNext(Instant now, Duration lease);

    boolean markPublished(UUID eventId, UUID attemptToken, Instant at);

    boolean markReturned(UUID eventId, UUID attemptToken, String reason, Instant at);

    boolean retry(UUID eventId, UUID attemptToken, String reason, Instant at);

    record OutgoingEvent(UUID eventId, UUID caseId, long aggregateRevision, int order,
                         String eventType, int eventVersion, String correlationId,
                         byte[] payload, Instant occurredAt) {
        public OutgoingEvent {
            if (eventId == null || caseId == null || aggregateRevision < 0 || order < 0
                    || eventType == null || eventType.isBlank() || eventType.length() > 100
                    || eventVersion < 1 || correlationId == null || correlationId.isBlank()
                    || correlationId.length() > 128 || payload == null || payload.length == 0
                    || payload.length > 1_048_576 || occurredAt == null) {
                throw new IllegalArgumentException("Surgery outbox event không hợp lệ");
            }
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    record Delivery(UUID eventId, UUID caseId, long aggregateRevision, int order,
                    String eventType, int eventVersion, String correlationId,
                    byte[] payload, UUID attemptToken, Instant leaseUntil, int attempts) {
        public Delivery {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
