package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class SettlementTest {

    @Test
    void create_paidInFullWithZeroBalance_succeeds() {
        Settlement settlement = create(BigDecimal.ZERO, SettlementOutcome.PAID_IN_FULL);

        assertThat(settlement.getOutcome()).isEqualTo(SettlementOutcome.PAID_IN_FULL);
    }

    @Test
    void create_paidInFullWithPositiveBalance_throwsBillingSettlementOutcomeMismatch() {
        assertThatThrownBy(() -> create(new BigDecimal("1000.00"), SettlementOutcome.PAID_IN_FULL))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("không khớp dấu");
    }

    @Test
    void create_additionalPaymentRequiredWithPositiveBalance_succeeds() {
        Settlement settlement = create(new BigDecimal("50000.00"), SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED);

        assertThat(settlement.getBalance()).isEqualByComparingTo("50000.00");
    }

    @Test
    void create_refundDueWithNegativeBalance_succeeds() {
        Settlement settlement = create(new BigDecimal("-20000.00"), SettlementOutcome.REFUND_DUE);

        assertThat(settlement.getOutcome()).isEqualTo(SettlementOutcome.REFUND_DUE);
    }

    @Test
    void create_debtApprovedWithPositiveBalance_succeeds() {
        Settlement settlement = create(new BigDecimal("50000.00"), SettlementOutcome.DEBT_APPROVED);

        assertThat(settlement.getOutcome()).isEqualTo(SettlementOutcome.DEBT_APPROVED);
    }

    @Test
    void create_invalidVersion_throwsBillingSettlementInvalidVersion() {
        assertThatThrownBy(() -> Settlement.create(UUID.randomUUID(), UUID.randomUUID(), 0, null,
                new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("100000.00"),
                new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                SettlementOutcome.PAID_IN_FULL, Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }

    private Settlement create(BigDecimal balance, SettlementOutcome outcome) {
        return Settlement.create(UUID.randomUUID(), UUID.randomUUID(), 1, null,
                new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("100000.00"),
                new BigDecimal("100000.00"), BigDecimal.ZERO, balance, outcome, Instant.now());
    }
}
