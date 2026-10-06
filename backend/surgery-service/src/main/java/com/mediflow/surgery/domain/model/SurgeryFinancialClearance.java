package com.mediflow.surgery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;

/** Billing authority for one exact case. It satisfies no clinical, consent, team or room guard. */
public record SurgeryFinancialClearance(UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
        UUID surgeryCaseId, CareEpisodeType episodeType, UUID episodeId, UUID admissionId,
        BigDecimal amount, String currency, String paymentMethod, Instant grantedAt, Instant expiresAt,
        String fingerprint) {
    public SurgeryFinancialClearance {
        if (clearanceId == null || invoiceId == null || accountId == null || patientId == null || surgeryCaseId == null
                || episodeType == null || episodeId == null || grantedAt == null || amount == null || amount.signum() < 0
                || amount.stripTrailingZeros().scale() > 2 || amount.precision() - amount.scale() > 17
                || currency == null || !currency.matches("[A-Z]{3}") || paymentMethod == null || paymentMethod.isBlank()
                || paymentMethod.length() > 40
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                || expiresAt != null && !expiresAt.isAfter(grantedAt)
                || episodeType == CareEpisodeType.ADMISSION && !episodeId.equals(admissionId)
                || episodeType == CareEpisodeType.OUTPATIENT_VISIT && admissionId != null) {
            throw new SurgeryRuleException("SURGERY_CLEARANCE_INVALID", "Invalid exact Surgery financial authority");
        }
    }
    public boolean matches(SurgeryCase surgeryCase) {
        CareEpisode episode = surgeryCase.getCareEpisode();
        return surgeryCaseId.equals(surgeryCase.getSurgeryCaseId()) && patientId.equals(surgeryCase.getPatientId())
                && episodeType == episode.type() && episodeId.equals(episode.episodeId())
                && java.util.Objects.equals(admissionId, episode.admissionId());
    }
    public boolean isValidFor(SurgeryCase surgeryCase, Instant at) {
        return at != null && matches(surgeryCase) && !at.isBefore(grantedAt)
                && (expiresAt == null || at.isBefore(expiresAt));
    }
}
