package com.mediflow.billing.application.dto.command;

import java.util.UUID;

/**
 * Exact-match discovery query for {@code GET /api/v1/billing/payment-requests/by-source}
 * (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §3). {@code sourceId} is the appointmentId for
 * EXAM, the labId for LAB_TEST, or the prescriptionId for PRESCRIPTION. All four expectations must
 * match the persisted Billing relationship exactly; none of them grant access by themselves.
 */
public record PaymentRequestSourceQuery(PaymentRequestSourcePurpose purpose, UUID sourceId,
                                         UUID patientId, UUID careEpisodeId) {
    public PaymentRequestSourceQuery {
        if (purpose == null || sourceId == null || patientId == null || careEpisodeId == null) {
            throw new IllegalArgumentException("purpose, sourceId, patientId and careEpisodeId are all required");
        }
    }
}
