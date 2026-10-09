package com.mediflow.notification.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundNoticeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID refundTransactionId, UUID originalTransactionId, UUID patientId,
        BigDecimal amount, String currency) {
    public RefundNoticeCommand {
        if (eventId == null || refundTransactionId == null || originalTransactionId == null || patientId == null
                || refundTransactionId.equals(originalTransactionId)
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[0-9a-f]{64}")
                || sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2
                || amount.precision() - amount.scale() > 17 || !"VND".equals(currency))
            throw new IllegalArgumentException("Invalid completed-refund notice");
    }
}
