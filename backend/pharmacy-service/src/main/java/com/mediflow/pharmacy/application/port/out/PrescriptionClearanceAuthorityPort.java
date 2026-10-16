package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.model.PrescriptionClearance;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Current Billing observation, never a distributed lease or a replacement for the local target lock. */
public interface PrescriptionClearanceAuthorityPort {
    Observation lookup(UUID clearanceId, String correlationId);

    record Observation(boolean exists, UUID clearanceId, boolean eligible, UUID invoiceId, UUID accountId,
            UUID patientId, String purpose, String careEpisodeType, UUID careEpisodeId, UUID admissionId,
            UUID surgeryCaseId, Instant grantedAt, Instant expiresAt, Instant observedAt) {
        public void requireCurrent(PrescriptionClearance grant, Instant at) {
            if (at == null || observedAt == null || observedAt.isBefore(at.minusSeconds(5))
                    || observedAt.isAfter(at.plusSeconds(2))) throw new PharmacyUpstreamUnavailableException();
            if (!exists || !eligible || !grant.clearanceId().equals(clearanceId)
                    || !grant.invoiceId().equals(invoiceId) || !grant.accountId().equals(accountId)
                    || !grant.patientId().equals(patientId) || !"PRESCRIPTION".equals(purpose)
                    || !"OUTPATIENT_VISIT".equals(careEpisodeType) || !grant.episode().id().equals(careEpisodeId)
                    || admissionId != null || surgeryCaseId != null || !grant.grantedAt().equals(grantedAt)
                    || !Objects.equals(grant.expiresAt(), expiresAt) || !grant.isValidAt(at)) {
                throw new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED",
                        "Current Billing authorization does not allow this outpatient prescription");
            }
        }
    }
}
