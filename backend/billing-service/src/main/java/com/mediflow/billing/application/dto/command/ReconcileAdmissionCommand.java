package com.mediflow.billing.application.dto.command;

import java.util.UUID;

public record ReconcileAdmissionCommand(UUID accountId, UUID actorId, String idempotencyKey) {
    public ReconcileAdmissionCommand {
        if (accountId == null || actorId == null || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,120}")) {
            throw new com.mediflow.billing.domain.exception.BillingRuleException("BILLING_RECONCILIATION_INVALID_REQUEST",
                    "Account, authenticated actor and bounded idempotency key required");
        }
    }
}
