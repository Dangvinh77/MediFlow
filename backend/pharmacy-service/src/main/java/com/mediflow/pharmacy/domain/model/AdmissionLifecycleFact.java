package com.mediflow.pharmacy.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;

/** Exact Inpatient fact, never an admission inferred from patient or record identity. */
public record AdmissionLifecycleFact(
        Kind kind, UUID admissionId, UUID patientId, UUID departmentId,
        Instant effectiveAt, String fingerprint) {

    public enum Kind { STARTED, MEDICALLY_DISCHARGED, CLOSED }

    public AdmissionLifecycleFact {
        if (kind == null || admissionId == null || patientId == null || effectiveAt == null
                || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")
                || (kind == Kind.STARTED && departmentId == null)
                || (kind != Kind.STARTED && departmentId != null)) {
            throw new PrescriptionRuleException("ADMISSION_FACT_INVALID", "Incomplete admission lifecycle fact");
        }
    }
}
