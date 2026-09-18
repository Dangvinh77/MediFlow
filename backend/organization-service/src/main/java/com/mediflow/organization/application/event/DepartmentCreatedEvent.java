package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

/** Integration event emitted after a department is created. */
public record DepartmentCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID departmentId,
        String departmentName,
        String departmentType) {

    public static final String ROUTING_KEY = "department.created";
}
