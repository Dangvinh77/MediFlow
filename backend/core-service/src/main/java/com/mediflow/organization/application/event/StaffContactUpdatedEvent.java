package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

/** Signals a contact change without distributing private contact values. */
public record StaffContactUpdatedEvent(UUID eventId, Instant occurredAt, String correlationId, UUID staffId) {
    public static final String ROUTING_KEY = "staff.contact.updated";
}
