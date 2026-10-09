package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.mediflow.billing.domain.model.PaymentClassification;
import com.mediflow.billing.domain.model.PaymentTransactionStatus;

public record LedgerRefundDTO(UUID refundTransactionId, UUID originalTransactionId, UUID accountId,
        UUID paymentRequestId, PaymentClassification classification, PaymentTransactionStatus status,
        BigDecimal amount, String currency, String paymentMethod, Instant completedAt) { }
