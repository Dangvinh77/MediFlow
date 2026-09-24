package com.mediflow.patient.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable application event contract consumed by Notification on {@code patient.created}.
 *
 * <p>The Vietnamese field names are intentional and must not be replaced with persistence names
 * or English DTO aliases. The event is published only after the patient transaction commits.</p>
 */
public record PatientCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        String hoTen,
        String email,
        String sdt) {
}
