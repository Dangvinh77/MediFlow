package com.mediflow.inpatient.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AdmissionLookupDTO(boolean exists, UUID admissionId, UUID patientId,
        UUID departmentId, UUID sourceRecordId, String status, boolean eligible,
        String sourceRevision, Instant observedAt) {}
