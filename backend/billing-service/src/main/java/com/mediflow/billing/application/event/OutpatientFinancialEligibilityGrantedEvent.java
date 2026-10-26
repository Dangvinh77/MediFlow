package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/** A.7/A.10 eligibility fact; publication needs its own insert-time acceptance gate. */
public record OutpatientFinancialEligibilityGrantedEvent(UUID eventId, String eventType, int version,
        Instant occurredAt, String correlationId, String producer, EligibilityFact payload) {
    public record EligibilityFact(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, long fenceToken, UUID patientId, String sourceType, UUID sourceId,
            String careEpisodeType, UUID careEpisodeId, UUID transactionId, UUID clearanceId,
            String amount, String currency, Instant completedAt, String basis, UUID quoteId) {
        public EligibilityFact(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
                UUID reservationId, long fenceToken, UUID patientId, String sourceType, UUID sourceId,
                String careEpisodeType, UUID careEpisodeId, UUID transactionId, UUID clearanceId,
                String amount, String currency, Instant completedAt, String basis) {
            this(paymentOperationId, paymentRequestId, registrationId, reservationId, fenceToken, patientId,
                    sourceType, sourceId, careEpisodeType, careEpisodeId, transactionId, clearanceId, amount,
                    currency, completedAt, basis, null);
        }
    }
}
