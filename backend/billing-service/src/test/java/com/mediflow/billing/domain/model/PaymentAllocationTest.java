package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class PaymentAllocationTest {

    @Test
    void allocate_positiveAmount_succeeds() {
        PaymentAllocation allocation = PaymentAllocation.allocate(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("50000.00"), Instant.now());

        assertThat(allocation.getAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void allocate_zeroAmount_throwsBillingAllocationInvalidAmount() {
        assertThatThrownBy(() -> PaymentAllocation.allocate(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO, Instant.now()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("lớn hơn 0");
    }
}
