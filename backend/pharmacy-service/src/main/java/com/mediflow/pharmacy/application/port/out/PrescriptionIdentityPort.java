package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.time.Instant;
import java.util.UUID;

/** Current identity facts; neither a medication order nor a distributed permission lease. */
public interface PrescriptionIdentityPort {
    Observation lookup(UUID patientId, UUID doctorId, UUID departmentId, String correlationId);

    /** checkedAt is the local start of preflight, not an invented upstream revision/time. */
    record Observation(UUID patientId, boolean patientExists, UUID doctorId, boolean doctorExists,
            boolean eligibleDoctor, UUID doctorDepartmentId, UUID departmentId, boolean departmentActive,
            Instant checkedAt) {
        public void requireExact(UUID patient, UUID doctor, UUID department, Instant at) {
            if (checkedAt == null || at == null || checkedAt.isBefore(at.minusSeconds(30))
                    || checkedAt.isAfter(at.plusSeconds(5))) throw new PharmacyUpstreamUnavailableException();
            if (patient == null || doctor == null || department == null || !patient.equals(patientId)
                    || !doctor.equals(doctorId) || !department.equals(departmentId))
                throw new PharmacyUpstreamUnavailableException();
            if (!patientExists) throw denied("PHARMACY_PATIENT_NOT_FOUND", "Patient does not exist");
            if (!doctorExists || !eligibleDoctor)
                throw denied("PHARMACY_PRESCRIBER_INELIGIBLE", "An active doctor is required");
            if (!departmentActive || !department.equals(doctorDepartmentId))
                throw denied("PHARMACY_PRESCRIBER_DEPARTMENT_MISMATCH", "Active prescribing department must match");
        }
        private static BusinessRuleException denied(String code, String message) {
            return new BusinessRuleException(code, message);
        }
    }
}
