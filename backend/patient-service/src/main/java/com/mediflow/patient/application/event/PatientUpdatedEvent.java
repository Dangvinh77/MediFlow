package com.mediflow.patient.application.event;

import java.time.Instant;
import java.util.UUID;

public record PatientUpdatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        String hoTen,
        String email,
        String sdt,
        String diaChi) {
}
