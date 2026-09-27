package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InpatientOutboxPort {
    void append(UUID aggregateId, DomainEventEnvelope<?> event);
    List<PendingOutboxEvent> claimPending(int limit);
    void markPublished(UUID eventId, Instant publishedAt);
    void markPublishFailed(UUID eventId, String reason);

    record PendingOutboxEvent(UUID eventId, String eventType, String payloadJson) {
    }
}
