package com.mediflow.organization.application.event;

import java.time.Instant;
import java.util.UUID;

public record DepartmentHeadChangedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID departmentId,
        long revision,
        UUID previousAppointmentId,
        UUID appointmentId,
        UUID staffId,
        UUID accountId,
        UUID actorAccountId) {}
