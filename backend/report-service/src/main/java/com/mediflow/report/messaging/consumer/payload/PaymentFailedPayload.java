package com.mediflow.report.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

/** Wire projection of billing-service's payment.failed event. */
public record PaymentFailedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId
) {
}
