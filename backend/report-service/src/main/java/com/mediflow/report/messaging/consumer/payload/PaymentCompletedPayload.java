package com.mediflow.report.messaging.consumer.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Wire projection of billing-service's payment.completed event. */
public record PaymentCompletedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID departmentId,
        BigDecimal totalAmount
) {
}
