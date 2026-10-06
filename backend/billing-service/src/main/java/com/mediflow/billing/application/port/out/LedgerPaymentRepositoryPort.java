package com.mediflow.billing.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.ClearanceTarget;
import com.mediflow.billing.domain.model.FinancialClearance;
import com.mediflow.billing.domain.model.PaymentRequest;
import com.mediflow.billing.domain.model.PaymentTransaction;

/** Lock and every following write participate in one local Billing transaction. */
public interface LedgerPaymentRepositoryPort {
    void lockIdempotencyKey(String key);
    PaymentContext lockRequest(UUID requestId);
    void verifyRequestTargets(PaymentContext context);
    Optional<RecordedPayment> findByIdempotencyKey(String key);
    void save(PaymentTransaction transaction, PaymentRequest request, UUID actorAccountId);
    FinancialClearance saveClearance(FinancialClearance clearance);
    void allocate(PaymentTransaction transaction);

    record PaymentContext(BillingAccount account, PaymentRequest request, ClearanceTarget target,
                          BigDecimal completedAmount) { }
    record RecordedPayment(PaymentTransaction transaction, UUID actorAccountId) { }
}
