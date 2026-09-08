package com.mediflow.clinical.application.event;

import java.util.UUID;
import java.time.Instant;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
/** Status enum serializes to the string consumed by billing. Publish after commit. */
public record AppointmentStatusChangedEvent(
        UUID eventId, Instant occurredAt, String correlationId,
        UUID appointmentId, UUID recordId, UUID patientId, UUID departmentId, AppointmentStatus status
) {
    public static final String ROUTING_KEY = "appointment.status.changed";

    /**
     * recordId is known when creating a record, but nullable for a standalone arrival.
     * Billing must defer its record-based EXAM fee when this correlation is absent.
     */
    public static AppointmentStatusChangedEvent from(Appointment appointment, UUID recordId, String correlationId) {
        return new AppointmentStatusChangedEvent(UUID.randomUUID(), Instant.now(), correlationId,
                appointment.getAppointmentId(), recordId, appointment.getPatientId(),
                appointment.getDepartmentId(), appointment.getStatus());
    }
}
