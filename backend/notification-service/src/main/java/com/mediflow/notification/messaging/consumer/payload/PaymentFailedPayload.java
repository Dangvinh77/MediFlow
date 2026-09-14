package com.mediflow.notification.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Local wire projection khớp hợp đồng của billing-service ({@code payment.failed},
 * backend-spec/06-billing.md §9). {@code invoiceId}/{@code reason} đổ vào biến
 * {@code maHoaDon}/{@code reason} của {@code NotificationTemplates}.
 */
public record PaymentFailedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID patientId,
        String reason
) {}
