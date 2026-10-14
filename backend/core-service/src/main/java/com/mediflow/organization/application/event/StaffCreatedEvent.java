package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

/** Integration event emitted after a staff member is created. */
public record StaffCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID staffId,
        String fullName,
        UUID departmentId,
        String jobTitle) {

    public static final String ROUTING_KEY = "staff.created";
}
