package com.mediflow.clinical.application.dto.authority;

import java.time.Instant;
import java.util.UUID;

/** Durable snapshot claimed by the Clinical payment reconciliation worker. */
public record PaymentRecoveryOperation(
        UUID paymentOperationId,
        UUID paymentRequestId,
        UUID registrationId,
        UUID reservationId,
        UUID patientId,
        String sourceType,
        UUID sourceId,
        UUID careEpisodeId,
        Long fenceToken,
        String basis,
        UUID quoteId,
        Instant reconcileAfter,
        Instant firstAttemptAt,
        int attemptCount,
        UUID recoveryOwner) {
}
