package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import java.time.Instant;
import java.util.UUID;

/** Minimal internal read model; admission state is not current bed-placement authority. */
public record AdmissionAuthoritySnapshot(UUID admissionId, UUID patientId, UUID departmentId,
        UUID sourceRecordId, AdmissionStatus status, long sourceRevision,
        Instant medicallyDischargedAt, Instant closedAt, Instant cancelledAt) {
    public boolean eligible() {
        return status == AdmissionStatus.ADMITTED && medicallyDischargedAt == null
                && closedAt == null && cancelledAt == null;
    }
}
