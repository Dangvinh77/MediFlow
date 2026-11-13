package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

/** Retains profile identity while announcing the end of active employment. */
public record StaffDeactivatedEvent(UUID eventId, Instant occurredAt, String correlationId,
                                    UUID staffId, UUID actorAccountId) {
    public static final String ROUTING_KEY = "staff.deactivated";
}
