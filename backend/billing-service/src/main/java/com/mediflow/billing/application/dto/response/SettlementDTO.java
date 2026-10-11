package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.model.SettlementOutcome;

public record SettlementDTO(UUID settlementId, UUID accountId, UUID admissionId, int settlementVersion,
                            BigDecimal grossAmount, BigDecimal insuranceAmount, BigDecimal patientLiability,
                            BigDecimal completedPayments, BigDecimal completedRefunds, BigDecimal balance,
                            SettlementOutcome outcome, Instant completedAt, UUID settlementPaymentRequestId) { }
