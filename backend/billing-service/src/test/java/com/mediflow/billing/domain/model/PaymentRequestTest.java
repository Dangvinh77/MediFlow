package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class PaymentRequestTest {

    @Test
    void open_valid_startsPending() {
        PaymentRequest request = open(new BigDecimal("250000.00"));

        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(request.isSettled()).isFalse();
    }

    @Test
    void open_negativeAmount_throwsBillingPaymentRequestInvalid() {
        assertThatThrownBy(() -> open(new BigDecimal("-1")))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("không được âm");
    }

    @Test
    void markPaid_fromPending_setsPaidAndCompletedAt() {
        PaymentRequest request = open(new BigDecimal("250000.00"));

        request.markPaid(Instant.now());

        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(request.isSettled()).isTrue();
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    void markPartiallyPaid_thenMarkPaid_isValidPath() {
        PaymentRequest request = open(new BigDecimal("250000.00"));

        request.markPartiallyPaid();
        request.markPaid(Instant.now());

        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
    }

    @Test
    void cancel_afterPaid_rejects() {
        PaymentRequest request = open(new BigDecimal("250000.00"));
        request.markPaid(Instant.now());

        assertThatThrownBy(request::cancel)
                .isInstanceOf(BillingRuleException.class);
    }

    private PaymentRequest open(BigDecimal amount) {
        return PaymentRequest.open(UUID.randomUUID(), UUID.randomUUID(), PaymentRequestPurpose.EXAM,
                amount, "VND", null, UUID.randomUUID(), Instant.now());
    }
}
