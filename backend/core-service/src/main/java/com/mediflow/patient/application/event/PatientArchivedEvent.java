package com.mediflow.patient.application.event;

import java.time.Instant;
import java.util.UUID;

/** Archive retains the patient identity and historical references. */
public record PatientArchivedEvent(UUID eventId, Instant occurredAt, String correlationId, UUID patientId) {}
