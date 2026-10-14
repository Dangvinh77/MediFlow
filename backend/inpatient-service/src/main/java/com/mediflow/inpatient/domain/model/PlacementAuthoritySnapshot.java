package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlacementAuthoritySnapshot(
        UUID admissionId,
        UUID patientId,
        AdmissionStatus admissionStatus,
        Instant medicallyDischargedAt,
        Instant closedAt,
        Instant cancelledAt,
        long placementRevision,
        UUID assignmentId,
        UUID bedId,
        UUID placementDepartmentId,
        String wardCode,
        Instant assignedAt) {

    public PlacementAuthoritySnapshot {
        Objects.requireNonNull(admissionId, "admissionId");
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(admissionStatus, "admissionStatus");
        if (placementRevision < 0) {
            throw new IllegalArgumentException("placementRevision must not be negative");
        }
        boolean noPlacement = assignmentId == null && bedId == null
                && placementDepartmentId == null && wardCode == null && assignedAt == null;
        boolean completePlacement = assignmentId != null && bedId != null
                && placementDepartmentId != null && wardCode != null && !wardCode.isBlank()
                && assignedAt != null;
        if (!noPlacement && !completePlacement) {
            throw new IllegalArgumentException("Current placement must be complete or absent");
        }
    }

    public boolean medicationWindowOpen() {
        return admissionStatus == AdmissionStatus.ADMITTED
                && medicallyDischargedAt == null && closedAt == null && cancelledAt == null;
    }

    public boolean hasPlacement() {
        return assignmentId != null;
    }
}
