package com.mediflow.billing.application.dto.command;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.util.UUID;

public record FinalizeAdmissionCommand(UUID accountId,UUID actorId,String idempotencyKey) {
    public FinalizeAdmissionCommand {
        if(accountId==null || actorId==null || idempotencyKey==null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,120}"))
            throw new BillingRuleException("BILLING_FINAL_SETTLEMENT_INVALID_REQUEST","Account, signed actor and bounded key required");
    }
}
