package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.time.Instant;
import java.util.UUID;

/** Admission state only: no placement, prescriber or order relationship permission is implied. */
public interface AdmissionAuthorityPort {
    Observation findAdmission(UUID admissionId, String correlationId);

    record Observation(UUID admissionId, UUID patientId, UUID departmentId, UUID sourceRecordId,
            boolean exists, boolean eligible, String status, String sourceRevision, Instant observedAt) {
        /** A necessary exact-state/freshness check, not sufficient authorization to dispense. */
        public void requireExactActive(UUID expectedPatient, UUID expectedDepartment, Instant at) {
            if (at == null || observedAt == null || observedAt.isBefore(at.minusSeconds(30))
                    || observedAt.isAfter(at.plusSeconds(5))) throw new PharmacyUpstreamUnavailableException();
            if (!exists || !eligible || !"ADMITTED".equals(status))
                throw new BusinessRuleException("PHARMACY_ADMISSION_INACTIVE", "Admission is not medication eligible");
            if (expectedPatient == null || expectedDepartment == null || !expectedPatient.equals(patientId)
                    || !expectedDepartment.equals(departmentId))
                throw new BusinessRuleException("PHARMACY_CARE_CONTEXT_INVALID", "Admission context does not match");
        }
    }
}
