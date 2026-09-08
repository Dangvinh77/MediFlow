package com.mediflow.billing.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Billing projection of lab.result.created; labId is the source testId. */
public record LabResultCreatedEvent(
        UUID eventId, Instant occurredAt, String correlationId,
        UUID labId, UUID patientId, UUID recordId, UUID departmentId,
        String labType, LocalDate performedDate
) {
    /** Compatibility with older producers; resolve missing type through the local projection. */
    public LabResultCreatedEvent(UUID eventId, Instant occurredAt, String correlationId,
                                 UUID labId, UUID patientId, UUID recordId, UUID departmentId) {
        this(eventId, occurredAt, correlationId, labId, patientId, recordId, departmentId, null, null);
    }
}
