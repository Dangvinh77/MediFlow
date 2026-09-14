package com.mediflow.notification.messaging.consumer.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Local wire projection khớp hợp đồng của billing-service ({@code payment.completed},
 * backend-spec/06-billing.md §9). {@code invoiceId}/{@code totalAmount} đổ vào biến
 * {@code maHoaDon}/{@code tongTien} của {@code NotificationTemplates}.
 */
public record PaymentCompletedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID patientId,
        BigDecimal totalAmount
) {}
