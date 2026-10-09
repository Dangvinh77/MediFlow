package com.mediflow.billing.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.PaymentTransaction;

/** Account lock is shared with payments; original and completed money rows remain immutable. */
public interface LedgerRefundRepositoryPort {
    RefundContext lockOriginal(UUID originalTransactionId);
    Optional<String> refundReason(UUID refundTransactionId);
    void append(PaymentTransaction refund, String reason, UUID actorAccountId);
    void reverseAllocations(PaymentTransaction original, PaymentTransaction refund);
    void revokeUnsatisfiedClearance(UUID paymentRequestId, Instant revokedAt);

    record RefundContext(BillingAccount account, PaymentTransaction original, BigDecimal completedRefunds) { }
}
