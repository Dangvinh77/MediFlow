package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape for {@code GET /api/v1/billing/payment-requests/by-source}
 * (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §3). {@code paidAmount} is the sum of completed
 * PAYMENT transactions only — refunds/reversals never reopen a PAID request here. A snapshot read,
 * not a lease or a clearance grant.
 */
public record PaymentRequestDiscoveryDTO(
        UUID paymentRequestId, UUID invoiceId, UUID accountId, UUID patientId, UUID departmentId,
        String purpose, String sourceType, UUID sourceId, UUID careEpisodeId, String careEpisodeType,
        UUID appointmentId, UUID recordId, UUID prescriptionId, List<UUID> labTestIds,
        BigDecimal requestedAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
        String currency, String status, String accountStatus, Instant expiresAt, Instant observedAt) {
    public PaymentRequestDiscoveryDTO {
        labTestIds = labTestIds == null ? List.of() : List.copyOf(labTestIds);
    }
}
