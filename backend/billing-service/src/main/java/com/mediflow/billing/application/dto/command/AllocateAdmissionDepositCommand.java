package com.mediflow.billing.application.dto.command;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record AllocateAdmissionDepositCommand(UUID accountId, UUID originalTransactionId, UUID actorId,
        String idempotencyKey, Map<UUID, BigDecimal> amounts) {
    public AllocateAdmissionDepositCommand {
        if (accountId == null || originalTransactionId == null || actorId == null || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,120}") || amounts == null || amounts.isEmpty()
                || amounts.size() > 1000 || amounts.keySet().stream().anyMatch(java.util.Objects::isNull)
                || amounts.values().stream().anyMatch(java.util.Objects::isNull)) throw new com.mediflow.billing.domain.exception.BillingRuleException(
                        "BILLING_DEPOSIT_ALLOCATION_INVALID", "Invalid admission deposit allocation command");
        amounts = Map.copyOf(amounts);
    }
}
