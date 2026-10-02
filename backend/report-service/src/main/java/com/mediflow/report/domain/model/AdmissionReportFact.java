package com.mediflow.report.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Minimal immutable source evidence, NOT a metric contribution or a medical discharge. */
public record AdmissionReportFact(Kind kind, UUID admissionId, UUID patientId, UUID departmentId,
        UUID bedId, Instant businessAt, boolean emergency, UUID emergencyOverrideId,
        UUID settlementId, UUID approvedOverrideId) {
    public enum Kind { STARTED, CLOSED }

    public AdmissionReportFact {
        if (kind == null || admissionId == null || patientId == null || businessAt == null) {
            throw new IllegalArgumentException("Exact admission/patient/business timestamp are required");
        }
        if (kind == Kind.STARTED) {
            if (departmentId == null || bedId == null || emergency != (emergencyOverrideId != null)
                    || settlementId != null || approvedOverrideId != null) {
                throw new IllegalArgumentException("Invalid admission start evidence");
            }
        } else if (departmentId != null || bedId != null || emergency || emergencyOverrideId != null
                || ((settlementId == null) == (approvedOverrideId == null))) {
            throw new IllegalArgumentException("Administrative close requires exactly one settlement/override proof");
        }
    }
}
