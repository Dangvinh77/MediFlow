package com.mediflow.pharmacy.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;

/** CLOSED is absorbing, including when its event arrives before STARTED. */
public record AdmissionMedicationContext(
        UUID admissionId, UUID patientId, UUID departmentId,
        Instant startedAt, String startedFingerprint,
        Instant closedAt, String closedFingerprint, long version) {

    public AdmissionMedicationContext {
        if (admissionId == null || patientId == null || version < 0
                || (startedAt == null) != (startedFingerprint == null)
                || (startedAt == null) != (departmentId == null)
                || (closedAt == null) != (closedFingerprint == null)
                || (startedFingerprint != null && !startedFingerprint.matches("[a-f0-9]{64}"))
                || (closedFingerprint != null && !closedFingerprint.matches("[a-f0-9]{64}"))) {
            throw invalid("Incomplete admission context");
        }
        if (startedAt != null && closedAt != null && closedAt.isBefore(startedAt)) {
            throw invalid("Admission close precedes its start");
        }
    }

    public static AdmissionMedicationContext empty(UUID admissionId, UUID patientId) {
        return new AdmissionMedicationContext(admissionId, patientId, null, null, null, null, null, 0);
    }

    public AdmissionMedicationContext apply(AdmissionLifecycleFact fact) {
        if (!admissionId.equals(fact.admissionId()) || !patientId.equals(fact.patientId())) {
            throw conflict("Admission/patient identity changed");
        }
        if (fact.kind() == AdmissionLifecycleFact.Kind.STARTED) {
            if (startedFingerprint != null) {
                if (!startedFingerprint.equals(fact.fingerprint())) {
                    throw conflict("Admission start fact changed without a correction contract");
                }
                return this;
            }
            return new AdmissionMedicationContext(admissionId, patientId, fact.departmentId(),
                    fact.effectiveAt(), fact.fingerprint(), closedAt, closedFingerprint, version + 1);
        }
        if (closedFingerprint != null) {
            if (!closedFingerprint.equals(fact.fingerprint())) {
                throw conflict("Admission close fact changed without a correction contract");
            }
            return this;
        }
        return new AdmissionMedicationContext(admissionId, patientId, departmentId,
                startedAt, startedFingerprint, fact.effectiveAt(), fact.fingerprint(), version + 1);
    }

    /** This local fact check is necessary, not sufficient: transfer/freshness approval is still gated. */
    public void requireActive(UUID expectedPatientId, UUID expectedDepartmentId) {
        if (!patientId.equals(expectedPatientId) || expectedDepartmentId == null
                || !Objects.equals(departmentId, expectedDepartmentId)) {
            throw new PrescriptionRuleException("ADMISSION_CONTEXT_MISMATCH", "Admission patient/department mismatch");
        }
        if (startedAt == null || closedAt != null) {
            throw new PrescriptionRuleException("ADMISSION_NOT_ACTIVE", "Admission is not active");
        }
    }

    private static PrescriptionRuleException invalid(String message) {
        return new PrescriptionRuleException("ADMISSION_CONTEXT_INVALID", message);
    }

    private static PrescriptionRuleException conflict(String message) {
        return new PrescriptionRuleException("ADMISSION_FACT_CONFLICT", message);
    }
}
