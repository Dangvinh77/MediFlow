package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegistrationSettlementDTO(UUID registrationId, long registrationRevision, String kind, UUID paymentRequestId, BigDecimal amount,
        BigDecimal refundedAmount, BigDecimal outstandingAmount, String currency, String sourceType, UUID sourceId, UUID actorAccountId,
        String reason, Instant occurredAt) { }
