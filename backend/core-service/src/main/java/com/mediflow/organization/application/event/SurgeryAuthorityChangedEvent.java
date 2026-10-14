package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

public record SurgeryAuthorityChangedEvent(UUID eventId, String eventType, int version,
        Instant occurredAt, UUID correlationId, String producer, Payload payload) {
    public static final String ROUTING_KEY = "organization.surgery.authority.changed";

    public record Payload(String referenceKind, UUID referenceId, String teamRole,
                          long revision, UUID actorAccountId, String reason) {}
}
