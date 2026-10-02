package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class ChargeTest {

    @Test
    void post_valid_computesGrossAmount() {
        Charge charge = post(new BigDecimal("2"), new BigDecimal("150000.00"));

        assertThat(charge.getGrossAmount()).isEqualByComparingTo("300000.00");
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.POSTED);
        assertThat(charge.isPosted()).isTrue();
    }

    @Test
    void post_zeroQuantity_throwsBillingChargeInvalidQuantity() {
        assertThatThrownBy(() -> post(BigDecimal.ZERO, new BigDecimal("10000.00")))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("Số lượng");
    }

    @Test
    void post_negativeUnitAmount_throwsBillingChargeInvalidAmount() {
        assertThatThrownBy(() -> post(BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("Đơn giá");
    }

    @Test
    void voidCharge_withReason_marksVoided() {
        Charge charge = post(BigDecimal.ONE, new BigDecimal("10000.00"));

        charge.voidCharge("Bệnh nhân hủy khám");

        assertThat(charge.isPosted()).isFalse();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.VOIDED);
        assertThat(charge.getVoidReason()).isEqualTo("Bệnh nhân hủy khám");
    }

    @Test
    void voidCharge_withoutReason_throwsBillingChargeVoidReasonRequired() {
        Charge charge = post(BigDecimal.ONE, new BigDecimal("10000.00"));

        assertThatThrownBy(() -> charge.voidCharge(" "))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("lý do");
    }

    @Test
    void voidCharge_alreadyVoided_throwsBillingChargeAlreadyVoided() {
        Charge charge = post(BigDecimal.ONE, new BigDecimal("10000.00"));
        charge.voidCharge("Hủy lần 1");

        assertThatThrownBy(() -> charge.voidCharge("Hủy lần 2"))
                .isInstanceOf(BillingRuleException.class);
    }

    private Charge post(BigDecimal quantity, BigDecimal unitAmount) {
        return Charge.post(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EXAM",
                UUID.randomUUID(), "EXAM_GENERAL", "Khám tổng quát", quantity, unitAmount, Instant.now());
    }
}
