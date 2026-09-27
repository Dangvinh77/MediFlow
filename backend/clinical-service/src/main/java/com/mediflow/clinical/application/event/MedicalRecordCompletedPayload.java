package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.RecordDisposition;

public record MedicalRecordCompletedPayload(
        UUID recordId,
        UUID appointmentId,
        UUID patientId,
        UUID departmentId,
        RecordDisposition disposition,
        boolean admissionRequired,
        Instant completedAt
) {
}
