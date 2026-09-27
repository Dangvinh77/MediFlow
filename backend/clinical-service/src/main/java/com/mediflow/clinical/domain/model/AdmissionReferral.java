package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdmissionReferral {
    private final UUID admissionRequestId;
    private final UUID recordId;
    private final UUID patientId;
    private final UUID departmentId;
    private final UUID requestedBy;
    private final String diagnosisSummary;
    private final AdmissionPriority priority;
    private final boolean emergency;
    private final Instant requestedAt;

    public static AdmissionReferral request(UUID recordId, UUID patientId, UUID departmentId,
                                            UUID requestedBy, String diagnosisSummary,
                                            AdmissionPriority priority, boolean emergency,
                                            Instant requestedAt) {
        if (recordId == null || patientId == null || departmentId == null || requestedBy == null
                || diagnosisSummary == null || diagnosisSummary.isBlank() || diagnosisSummary.length() > 4000
                || priority == null || requestedAt == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Admission referral requires a record, actor, diagnosis summary and priority");
        }
        return new AdmissionReferral(UUID.randomUUID(), recordId, patientId, departmentId, requestedBy,
                diagnosisSummary, priority, emergency, requestedAt);
    }
}
