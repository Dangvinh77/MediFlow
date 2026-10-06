package com.mediflow.notification.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Financial receipt only. It cannot authorize care or assert a fully paid invoice. */
public record CarePaymentReceiptCommand(UUID eventId, String fingerprint, String correlationId,
        UUID transactionId, UUID patientId, BigDecimal amount, String currency, String classification) {
    public CarePaymentReceiptCommand {
        if (eventId == null || transactionId == null || patientId == null || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120 || amount == null || amount.signum() <= 0
                || currency == null || !currency.matches("[A-Z]{3}") || classification == null
                || !java.util.Set.of("SERVICE_PAYMENT", "ADMISSION_DEPOSIT", "SETTLEMENT_PAYMENT").contains(classification)) {
            throw new IllegalArgumentException("Invalid classified financial receipt");
        }
    }
}
