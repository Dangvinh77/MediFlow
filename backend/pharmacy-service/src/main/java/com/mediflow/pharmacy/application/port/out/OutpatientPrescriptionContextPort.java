package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.time.Instant;
import java.util.UUID;

/** Necessary Clinical relationship proof; not current staff eligibility, an order or a lease. */
public interface OutpatientPrescriptionContextPort {
    Observation findRecord(UUID recordId, String correlationId);

    record Observation(boolean exists, UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, String recordStatus, String disposition, Instant observedAt) {
        /** Caller must repeat this check after mutation lock waits; never substitute patient/latest record. */
        public void requireExact(UUID record, UUID patient, UUID doctor, UUID department, UUID episode, Instant at) {
            if (at == null || observedAt == null || observedAt.isBefore(at.minusSeconds(30))
                    || observedAt.isAfter(at.plusSeconds(5))) throw new PharmacyUpstreamUnavailableException();
            if (!exists || record == null || !record.equals(recordId) || patient == null || !patient.equals(patientId)
                    || doctor == null || !doctor.equals(doctorId) || department == null || !department.equals(departmentId)
                    || episode == null || !episode.equals(careEpisodeId) || !"OUTPATIENT_VISIT".equals(careEpisodeType))
                throw new BusinessRuleException("PHARMACY_CARE_CONTEXT_INVALID", "Clinical relationship does not match");
        }
    }
}
