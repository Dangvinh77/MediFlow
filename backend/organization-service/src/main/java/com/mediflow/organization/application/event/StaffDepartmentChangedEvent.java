package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

/** Integration event emitted after an existing staff member changes department. */
public record StaffDepartmentChangedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID staffId,
        UUID oldDepartmentId,
        UUID newDepartmentId) {

    public static final String ROUTING_KEY = "staff.department.changed";
}
