package com.mediflow.billing.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Selects allocation amounts only; no price, approval, patient, department or actor can be supplied. */
public record AllocateAdmissionDepositRequest(@NotNull UUID originalTransactionId,
        @NotNull @Size(min = 1, max = 1000) List<@NotNull @Valid Line> allocations) {
    public record Line(@NotNull UUID chargeId,
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) { }

    public com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand command(UUID accountId, UUID actorId, String key) {
        var amounts = new java.util.HashMap<UUID, BigDecimal>();
        for (var line : allocations) {
            if (amounts.putIfAbsent(line.chargeId(), line.amount()) != null) throw new com.mediflow.billing.domain.exception.BillingRuleException(
                    "BILLING_DEPOSIT_ALLOCATION_INVALID", "Duplicate charge allocation target");
        }
        return new com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand(accountId, originalTransactionId, actorId, key, amounts);
    }
}
