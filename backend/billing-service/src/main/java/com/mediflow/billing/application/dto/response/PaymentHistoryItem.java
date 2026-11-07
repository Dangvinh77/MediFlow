package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One completed ledger movement; {@code purpose} is null for a movement without a payment request. */
public record PaymentHistoryItem(UUID transactionId, UUID paymentRequestId, UUID patientId, UUID departmentId,
        String transactionType, String purpose, BigDecimal amount, String currency, String paymentMethod,
        Instant completedAt) {}
