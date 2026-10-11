package com.mediflow.notification.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Historical type name retained; purpose selects Surgery or Rx, never a paid receipt/care authority. */
public record SurgeryPaymentNoticeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID paymentRequestId, UUID patientId, BigDecimal totalAmount, String currency, String purpose) {
    public SurgeryPaymentNoticeCommand {
        if (eventId == null || paymentRequestId == null || patientId == null
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[0-9a-f]{64}")
                || sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || correlationId.chars().anyMatch(Character::isISOControl)
                || !java.util.Set.of("SURGERY", "PRESCRIPTION").contains(purpose)
                || totalAmount == null || totalAmount.signum() <= 0 || totalAmount.stripTrailingZeros().scale() > 2
                || totalAmount.precision() - totalAmount.scale() > 17 || !"VND".equals(currency))
            throw new IllegalArgumentException("Invalid Surgery payment notice");
    }
}
