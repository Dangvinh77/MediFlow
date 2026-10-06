package com.mediflow.surgery.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Durable inbox; begin, business mutation and applied marker share a transaction. */
public interface SurgeryInboxPort {

    Decision begin(IncomingEvent event);

    void markApplied(UUID eventId, Instant at);

    void defer(UUID eventId, String reason, Instant nextAttempt);

    void quarantine(UUID eventId, String reason);

    /** Read-only bounded selection. Concurrent workers are fenced by begin and the case row lock. */
    java.util.List<IncomingEvent> findDue(String eventType, Instant now, int limit);

    enum Decision { NEW, RETRY_PENDING, ALREADY_APPLIED, CONFLICT, QUARANTINED }

    record IncomingEvent(UUID eventId, String eventType, int version, String producer,
                         String fingerprint, String semanticKey, byte[] payload, Instant receivedAt) {
        public IncomingEvent {
            if (eventId == null || eventType == null || eventType.isBlank()
                    || eventType.length() > 100 || version < 1
                    || producer == null || producer.isBlank() || producer.length() > 100
                    || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                    || semanticKey == null || semanticKey.isBlank() || semanticKey.length() > 200
                    || payload == null || payload.length == 0 || payload.length > 1_048_576
                    || receivedAt == null) {
                throw new IllegalArgumentException("Surgery inbox event không hợp lệ");
            }
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
