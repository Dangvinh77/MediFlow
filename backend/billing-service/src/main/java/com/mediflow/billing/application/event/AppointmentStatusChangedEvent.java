package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/** Clinical status notification. EXAM fees deduplicate by recordId across clinical events. */
public record AppointmentStatusChangedEvent(
        UUID eventId, Instant occurredAt, String correlationId,
        UUID appointmentId, UUID patientId, UUID departmentId, String status, UUID recordId
) {
    /** Legacy status events may precede creation of a medical record. */
    public AppointmentStatusChangedEvent(UUID eventId, Instant occurredAt, String correlationId,
                                         UUID appointmentId, UUID patientId, UUID departmentId, String status) {
        this(eventId, occurredAt, correlationId, appointmentId, patientId, departmentId, status, null);
    }
}
