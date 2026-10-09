package com.mediflow.notification.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

/** A request for payment only; never a paid receipt, financial clearance or surgery booking. */
public record SurgeryPaymentNoticeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID paymentRequestId, UUID patientId, BigDecimal totalAmount, String currency) {
    public SurgeryPaymentNoticeCommand {
        if (eventId == null || paymentRequestId == null || patientId == null
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[0-9a-f]{64}")
                || sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || totalAmount == null || totalAmount.signum() <= 0 || totalAmount.stripTrailingZeros().scale() > 2
                || totalAmount.precision() - totalAmount.scale() > 17 || !"VND".equals(currency))
            throw new IllegalArgumentException("Invalid Surgery payment notice");
    }
}
