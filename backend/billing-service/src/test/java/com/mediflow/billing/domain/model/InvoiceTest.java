package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.domain.exception.BillingRuleException;

class InvoiceTest {

    @Test
    void create_emptyFeeList_throwsBillingNoUnpaidFees() {
        assertThatThrownBy(() -> Invoice.create(UUID.randomUUID(), LocalDate.now(), List.of()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("chưa thanh toán");
    }

    // BR-B2 — total_amount = Σ các khoản phí chưa trả, không nhận từ request
    @Test
    void create_sumsFeeAmounts() {
        Fee fee1 = fee(new BigDecimal("100000.00"));
        Fee fee2 = fee(new BigDecimal("50000.50"));

        Invoice invoice = Invoice.create(UUID.randomUUID(), LocalDate.now(), List.of(fee1, fee2));

        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("150000.50");
        assertThat(invoice.getSagaStatus()).isEqualTo(SagaStatus.NONE);
        assertThat(invoice.isAlreadyPaid()).isFalse();
    }

    @Test
    void createFromPrescription_emptyFeeList_throwsBillingNoUnpaidFees() {
        assertThatThrownBy(() -> Invoice.createFromPrescription(UUID.randomUUID(), UUID.randomUUID(), List.of()))
                .isInstanceOf(BillingRuleException.class);
    }

    // BR-B9 — cửa vào saga: khác hóa đơn thường, bắt đầu thẳng ở AWAITING_PAYMENT
    @Test
    void createFromPrescription_startsAtAwaitingPayment() {
        Invoice invoice = Invoice.createFromPrescription(UUID.randomUUID(), UUID.randomUUID(),
                List.of(fee(new BigDecimal("200000.00"))));

        assertThat(invoice.getSagaStatus()).isEqualTo(SagaStatus.AWAITING_PAYMENT);
    }

    // BR-B1 — không thanh toán hóa đơn đã trả
    @Test
    void pay_alreadyPaid_throwsBusinessRule() {
        Invoice invoice = regularInvoice();
        invoice.pay(PaymentMethod.CASH, Instant.now());

        assertThatThrownBy(() -> invoice.pay(PaymentMethod.CASH, Instant.now()))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("đã được thanh toán");
    }

    @Test
    void pay_setsMethodAndTimestamp() {
        Invoice invoice = regularInvoice();
        Instant now = Instant.now();

        invoice.pay(PaymentMethod.TRANSFER, now);

        assertThat(invoice.isAlreadyPaid()).isTrue();
        assertThat(invoice.getPaymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(invoice.getPaidAt()).isEqualTo(now);
    }

    // BR-B9 — chuyển trạng thái saga được kiểm tra đúng máy trạng thái ở §3
    @Test
    void transitionSaga_followsValidPath() {
        Invoice invoice = sagaInvoice(); // AWAITING_PAYMENT

        invoice.transitionSaga(SagaStatus.PAID);
        invoice.transitionSaga(SagaStatus.AWAITING_DISPENSE);
        invoice.transitionSaga(SagaStatus.COMPLETED);

        assertThat(invoice.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void transitionSaga_completedToRefunded_throwsInvalidTransition() {
        Invoice invoice = sagaInvoice();
        invoice.transitionSaga(SagaStatus.PAID);
        invoice.transitionSaga(SagaStatus.AWAITING_DISPENSE);
        invoice.transitionSaga(SagaStatus.COMPLETED);

        assertThatThrownBy(() -> invoice.transitionSaga(SagaStatus.REFUNDED))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("Không thể chuyển trạng thái saga");
    }

    @Test
    void transitionSaga_regularInvoiceNeverLeavesNone() {
        Invoice invoice = regularInvoice(); // sagaStatus = NONE

        assertThatThrownBy(() -> invoice.transitionSaga(SagaStatus.AWAITING_PAYMENT))
                .isInstanceOf(BillingRuleException.class);
    }

    // BR-B4 — xuất thuốc thất bại thì đảo lại thanh toán
    @Test
    void refund_reversesPaymentAndEndsSagaAtRefunded() {
        Invoice invoice = sagaInvoice();
        invoice.transitionSaga(SagaStatus.PAID);
        invoice.pay(PaymentMethod.CASH, Instant.now());
        invoice.transitionSaga(SagaStatus.AWAITING_DISPENSE);

        invoice.refund();

        assertThat(invoice.isAlreadyPaid()).isFalse();
        assertThat(invoice.getSagaStatus()).isEqualTo(SagaStatus.REFUNDED);
    }

    // BR-B11 — xuất thuốc thành công thì gán phiếu xuất
    @Test
    void assignDispense_setsDispenseId() {
        Invoice invoice = sagaInvoice();
        UUID dispenseId = UUID.randomUUID();

        invoice.assignDispense(dispenseId);

        assertThat(invoice.getDispenseId()).isEqualTo(dispenseId);
    }

    private Fee fee(BigDecimal amount) {
        return Fee.create(UUID.randomUUID(), null, UUID.randomUUID(), null,
                FeeType.EXAM, LocalDate.now(), amount);
    }

    private Invoice regularInvoice() {
        return Invoice.create(UUID.randomUUID(), LocalDate.now(), List.of(fee(new BigDecimal("100000.00"))));
    }

    private Invoice sagaInvoice() {
        return Invoice.createFromPrescription(UUID.randomUUID(), UUID.randomUUID(),
                List.of(fee(new BigDecimal("200000.00"))));
    }
}
