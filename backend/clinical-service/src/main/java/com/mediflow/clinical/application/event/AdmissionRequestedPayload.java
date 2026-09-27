package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.AdmissionPriority;

public record AdmissionRequestedPayload(
        UUID admissionRequestId,
        UUID recordId,
        UUID patientId,
        UUID departmentId,
        UUID requestedBy,
        String diagnosisSummary,
        AdmissionPriority priority,
        boolean emergency,
        Instant requestedAt
) {
}
