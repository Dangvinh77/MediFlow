package com.mediflow.patient.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface PatientIdentityAuditPort {
    void record(UUID patientId, String reason, String previousIdentityNumber, String currentIdentityNumber,
            LocalDate previousDateOfBirth, LocalDate currentDateOfBirth, Instant occurredAt, UUID correlationId);
}
