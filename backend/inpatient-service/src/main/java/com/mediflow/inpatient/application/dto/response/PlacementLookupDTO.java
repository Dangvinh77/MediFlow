package com.mediflow.inpatient.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PlacementLookupDTO(
        boolean exists,
        UUID admissionId,
        UUID patientId,
        String admissionStatus,
        boolean medicationWindowOpen,
        long placementRevision,
        PlacementDTO placement,
        Instant observedAt) {
    public record PlacementDTO(UUID assignmentId, UUID bedId, UUID departmentId,
                               String wardCode, Instant assignedAt) {}
}
