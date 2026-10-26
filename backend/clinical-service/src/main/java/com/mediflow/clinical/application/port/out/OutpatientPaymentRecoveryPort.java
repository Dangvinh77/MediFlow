package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.application.dto.authority.PaymentRecoveryOperation;

/** Durable cross-process claim and retry state for expired payment-lock reconciliation. */
public interface OutpatientPaymentRecoveryPort {
    List<PaymentRecoveryOperation> claimDue(UUID owner, Instant now, Instant leaseUntil, int limit);

    void complete(PaymentRecoveryOperation operation);

    /** Returns true once, when the operation first crosses its five-minute alert threshold. */
    boolean defer(PaymentRecoveryOperation operation, Instant now, Instant retryAt, String errorCode);
}
