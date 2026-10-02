package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class PaymentTransactionTest {

    @Test
    void open_payment_startsPending() {
        PaymentTransaction transaction = openPayment();

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(transaction.isRefundOrReversal()).isFalse();
    }

    @Test
    void open_refundWithoutOriginal_throwsBillingRefundRequiresOriginal() {
        assertThatThrownBy(() -> PaymentTransaction.open(UUID.randomUUID(), UUID.randomUUID(),
                PaymentTransactionType.REFUND, PaymentClassification.SERVICE_PAYMENT,
                new BigDecimal("100000.00"), "VND", "CASH", null, "idem-1", null, Instant.now()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("giao dịch gốc");
    }

    @Test
    void open_refundWithOriginal_succeeds() {
        UUID original = UUID.randomUUID();
        PaymentTransaction refund = PaymentTransaction.open(UUID.randomUUID(), UUID.randomUUID(),
                PaymentTransactionType.REFUND, PaymentClassification.SERVICE_PAYMENT,
                new BigDecimal("100000.00"), "VND", "CASH", null, "idem-2", original, Instant.now());

        assertThat(refund.isRefundOrReversal()).isTrue();
        assertThat(refund.getOriginalTransactionId()).isEqualTo(original);
    }

    @Test
    void open_zeroAmount_throwsBillingTransactionInvalidAmount() {
        assertThatThrownBy(() -> PaymentTransaction.open(UUID.randomUUID(), UUID.randomUUID(),
                PaymentTransactionType.PAYMENT, PaymentClassification.SERVICE_PAYMENT,
                BigDecimal.ZERO, "VND", "CASH", null, "idem-3", null, Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    @Test
    void open_blankIdempotencyKey_throwsBillingIdempotencyKeyRequired() {
        assertThatThrownBy(() -> PaymentTransaction.open(UUID.randomUUID(), UUID.randomUUID(),
                PaymentTransactionType.PAYMENT, PaymentClassification.SERVICE_PAYMENT,
                new BigDecimal("1000.00"), "VND", "CASH", null, " ", null, Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    @Test
    void complete_fromPending_setsCompletedAndTimestamp() {
        PaymentTransaction transaction = openPayment();

        transaction.complete(Instant.now());

        assertThat(transaction.isCompleted()).isTrue();
        assertThat(transaction.getCompletedAt()).isNotNull();
    }

    @Test
    void complete_alreadyCompleted_throwsBillingTransactionAlreadyFinalized() {
        PaymentTransaction transaction = openPayment();
        transaction.complete(Instant.now());

        assertThatThrownBy(() -> transaction.complete(Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    private PaymentTransaction openPayment() {
        return PaymentTransaction.open(UUID.randomUUID(), UUID.randomUUID(), PaymentTransactionType.PAYMENT,
                PaymentClassification.SERVICE_PAYMENT, new BigDecimal("250000.00"), "VND", "CASH", null,
                "idem-" + UUID.randomUUID(), null, Instant.now());
    }
}
