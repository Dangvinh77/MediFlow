package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.PaymentContribution.TransitionEffect;

class PaymentContributionTest {

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID COMPLETED_EVENT_ID = UUID.randomUUID();
    private static final UUID FAILED_EVENT_ID = UUID.randomUUID();
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 8, 14);
    private static final BigDecimal AMOUNT = new BigDecimal("120.50");

    @Test
    void complete_newContribution_appliesAndStoresSourceData() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);

        TransitionEffect effect = contribution.complete(COMPLETED_EVENT_ID, PAYMENT_DATE, null, AMOUNT);

        assertThat(effect).isEqualTo(TransitionEffect.APPLY);
        assertThat(contribution.getStatus()).isEqualTo(PaymentContributionStatus.APPLIED);
        assertThat(contribution.getCompletedEventId()).isEqualTo(COMPLETED_EVENT_ID);
        assertThat(contribution.getPaymentDate()).isEqualTo(PAYMENT_DATE);
        assertThat(contribution.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void fail_appliedContribution_reversesOnce() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        contribution.complete(COMPLETED_EVENT_ID, PAYMENT_DATE, null, AMOUNT);

        TransitionEffect effect = contribution.fail(FAILED_EVENT_ID);

        assertThat(effect).isEqualTo(TransitionEffect.REVERSE);
        assertThat(contribution.getStatus()).isEqualTo(PaymentContributionStatus.REVERSED);
        assertThat(contribution.getFailedEventId()).isEqualTo(FAILED_EVENT_ID);
        assertThat(contribution.fail(UUID.randomUUID())).isEqualTo(TransitionEffect.NONE);
    }

    @Test
    void fail_newContribution_createsPendingReversalWithoutEffect() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);

        TransitionEffect effect = contribution.fail(FAILED_EVENT_ID);

        assertThat(effect).isEqualTo(TransitionEffect.NONE);
        assertThat(contribution.getStatus()).isEqualTo(PaymentContributionStatus.PENDING_REVERSAL);
        assertThat(contribution.getFailedEventId()).isEqualTo(FAILED_EVENT_ID);
    }

    @Test
    void complete_pendingReversal_marksReversedWithoutApplyingRevenue() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        contribution.fail(FAILED_EVENT_ID);

        TransitionEffect effect = contribution.complete(COMPLETED_EVENT_ID, PAYMENT_DATE, null, AMOUNT);

        assertThat(effect).isEqualTo(TransitionEffect.NONE);
        assertThat(contribution.getStatus()).isEqualTo(PaymentContributionStatus.REVERSED);
    }

    @Test
    void complete_appliedDuplicateWithSameData_isNoOp() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        contribution.complete(COMPLETED_EVENT_ID, PAYMENT_DATE, null, AMOUNT);

        TransitionEffect effect = contribution.complete(UUID.randomUUID(), PAYMENT_DATE, null, AMOUNT);

        assertThat(effect).isEqualTo(TransitionEffect.NONE);
        assertThat(contribution.getStatus()).isEqualTo(PaymentContributionStatus.APPLIED);
    }

    @Test
    void complete_appliedWithDifferentData_throwsConflict() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        contribution.complete(COMPLETED_EVENT_ID, PAYMENT_DATE, null, AMOUNT);

        assertThatThrownBy(() -> contribution.complete(UUID.randomUUID(), PAYMENT_DATE, null,
                new BigDecimal("121.00")))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_PAYMENT_CONFLICT"));
    }

    @Test
    void complete_invalidAmount_throwsReportRule() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);

        assertThatThrownBy(() -> contribution.complete(COMPLETED_EVENT_ID, PAYMENT_DATE, null,
                BigDecimal.ZERO))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_AMOUNT_INVALID"));
    }

    @Test
    void restore_pendingWithoutFailureEvent_rejectsPersistedState() {
        assertThatThrownBy(() -> PaymentContribution.restore(INVOICE_ID, null, null, null, null, null,
                PaymentContributionStatus.PENDING_REVERSAL, null, null))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_PERSISTED_DATA_INVALID"));
    }

    @Test
    void restore_appliedWithFailureEvent_rejectsPersistedState() {
        assertThatThrownBy(() -> PaymentContribution.restore(INVOICE_ID, COMPLETED_EVENT_ID,
                FAILED_EVENT_ID, PAYMENT_DATE, null, AMOUNT,
                PaymentContributionStatus.APPLIED, null, null))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_PERSISTED_DATA_INVALID"));
    }

    @Test
    void restore_pendingWithCompletedData_rejectsPersistedState() {
        assertThatThrownBy(() -> PaymentContribution.restore(INVOICE_ID, COMPLETED_EVENT_ID,
                FAILED_EVENT_ID, PAYMENT_DATE, null, AMOUNT,
                PaymentContributionStatus.PENDING_REVERSAL, null, null))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_PERSISTED_DATA_INVALID"));
    }
}
