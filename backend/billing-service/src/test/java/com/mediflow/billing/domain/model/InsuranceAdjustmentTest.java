package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class InsuranceAdjustmentTest {

    @Test
    void record_approval_succeeds() {
        InsuranceAdjustment adjustment = InsuranceAdjustment.record(UUID.randomUUID(), UUID.randomUUID(),
                "BHYT-DECISION-1", InsuranceAdjustmentType.APPROVAL, null, new BigDecimal("300000.00"),
                "Duyệt bảo hiểm", Instant.now());

        assertThat(adjustment.getAdjustmentType()).isEqualTo(InsuranceAdjustmentType.APPROVAL);
    }

    @Test
    void record_reversalWithoutOriginal_throwsBillingInsuranceReversalRequiresOriginal() {
        assertThatThrownBy(() -> InsuranceAdjustment.record(UUID.randomUUID(), UUID.randomUUID(),
                "BHYT-DECISION-1", InsuranceAdjustmentType.REVERSAL, null, new BigDecimal("300000.00"),
                "Đảo quyết định", Instant.now()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("điều chỉnh gốc");
    }

    @Test
    void record_reversalWithOriginal_succeeds() {
        UUID original = UUID.randomUUID();
        InsuranceAdjustment reversal = InsuranceAdjustment.record(UUID.randomUUID(), UUID.randomUUID(),
                "BHYT-DECISION-1", InsuranceAdjustmentType.REVERSAL, original, new BigDecimal("300000.00"),
                "Đảo quyết định", Instant.now());

        assertThat(reversal.getOriginalAdjustmentId()).isEqualTo(original);
    }

    @Test
    void record_negativeAmount_throwsBillingInsuranceAdjustmentInvalidAmount() {
        assertThatThrownBy(() -> InsuranceAdjustment.record(UUID.randomUUID(), UUID.randomUUID(),
                "BHYT-DECISION-1", InsuranceAdjustmentType.APPROVAL, null, new BigDecimal("-1"),
                "Duyệt bảo hiểm", Instant.now()))
                .isInstanceOf(BillingRuleException.class);
    }
}
