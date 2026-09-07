package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class FeeTest {

    @Test
    void create_negativeAmount_throwsBillingAmountNegative() {
        assertThatThrownBy(() -> create(new BigDecimal("-1.00")))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("không được âm");
    }

    @Test
    void create_missingDepartmentId_throwsBillingDeptRequired() {
        assertThatThrownBy(() -> Fee.create(UUID.randomUUID(), null, null, null,
                FeeType.EXAM, LocalDate.now(), new BigDecimal("50000.00")))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("khoa");
    }

    @Test
    void create_missingFeeType_throwsBillingDeptRequired() {
        assertThatThrownBy(() -> Fee.create(UUID.randomUUID(), null, UUID.randomUUID(), null,
                null, LocalDate.now(), new BigDecimal("50000.00")))
                .isInstanceOf(BillingRuleException.class);
    }

    @Test
    void create_valid_startsUnpaidWithNoInvoice() {
        Fee fee = create(new BigDecimal("50000.00"));

        assertThat(fee.isUnpaid()).isTrue();
        assertThat(fee.isPaid()).isFalse();
        assertThat(fee.getInvoiceId()).isNull();
    }

    @Test
    void markPaid_setsPaid() {
        Fee fee = create(new BigDecimal("50000.00"));

        fee.markPaid();

        assertThat(fee.isPaid()).isTrue();
        assertThat(fee.isUnpaid()).isFalse();
    }

    @Test
    void assignToInvoice_setsInvoiceId() {
        Fee fee = create(new BigDecimal("50000.00"));
        UUID invoiceId = UUID.randomUUID();

        fee.assignToInvoice(invoiceId);

        assertThat(fee.getInvoiceId()).isEqualTo(invoiceId);
    }

    @Test
    void refund_resetsPaidStatusAndDetachesFromInvoice() {
        Fee fee = create(new BigDecimal("50000.00"));
        fee.assignToInvoice(UUID.randomUUID());
        fee.markPaid();

        fee.refund();

        assertThat(fee.isPaid()).isFalse();
        assertThat(fee.getInvoiceId()).isNull();
    }

    private Fee create(BigDecimal amount) {
        return Fee.create(UUID.randomUUID(), null, UUID.randomUUID(), null,
                FeeType.EXAM, LocalDate.now(), amount);
    }
}
