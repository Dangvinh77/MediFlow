package com.mediflow.billing.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A current Billing-owned read, not a distributed reservation of money. */
public record FinancialClearanceAuthority(UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
        String purpose, String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID surgeryCaseId,
        Instant grantedAt, Instant expiresAt, Instant revokedAt, boolean ledgerEligible, Instant observedAt) {
    public boolean eligible() {
        return ledgerEligible && revokedAt == null && !observedAt.isBefore(grantedAt)
                && (expiresAt == null || observedAt.isBefore(expiresAt));
    }
}
