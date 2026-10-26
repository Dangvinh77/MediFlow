package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.util.UUID;

public record OutpatientEligibilityFact(
        UUID paymentOperationId,
        UUID paymentRequestId,
        UUID registrationId,
        UUID reservationId,
        long fenceToken,
        UUID patientId,
        String sourceType,
        UUID sourceId,
        String careEpisodeType,
        UUID careEpisodeId,
        UUID transactionId,
        UUID clearanceId,
        String amount,
        String currency,
        Instant completedAt,
        String basis,
        UUID quoteId) {
}
