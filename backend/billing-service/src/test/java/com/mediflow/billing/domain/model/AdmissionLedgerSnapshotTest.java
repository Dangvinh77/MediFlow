package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.mediflow.billing.domain.exception.BillingRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.mediflow.billing.domain.model.AdmissionLedgerSnapshot.*;

class AdmissionLedgerSnapshotTest {
    private final UUID account = UUID.randomUUID(), admission = UUID.randomUUID(), patient = UUID.randomUUID(), department = UUID.randomUUID();
    private final UUID charge = UUID.randomUUID(), pay = UUID.randomUUID(), refund = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T09:00:00Z");
    private BigDecimal amount(String amount) { return new BigDecimal(amount); }
    private AdmissionLedgerSnapshot snapshot(List<ChargeLine> charges, List<TransactionLine> transactions, List<AllocationLine> allocations, List<InsuranceLine> insurance) {
        return new AdmissionLedgerSnapshot(account, admission, patient, department, "VND", AccountStatus.CHARGE_CLOSED,
                now.minusSeconds(10), now, charges, transactions, allocations, insurance);
    }
    private ChargeLine charge(String value) { return new ChargeLine(charge, account, patient, "POSTED", amount(value)); }
    private TransactionLine payment(String value, String classification) {
        return new TransactionLine(pay, account, null, "PAYMENT", classification, "COMPLETED", amount(value), "VND", now.minusSeconds(60));
    }
    private TransactionLine returned(String value, String type, String classification) {
        return new TransactionLine(refund, account, pay, type, classification, "COMPLETED", amount(value), "VND", now.minusSeconds(5));
    }
    @Test void reconcile_unallocatedDeposit_isNotAllocatedPaymentOrRevenue() {
        var totals = snapshot(List.of(charge("80")), List.of(payment("100", "ADMISSION_DEPOSIT")), List.of(), List.of()).reconcile();
        assertThat(totals.completedPayments()).isEqualByComparingTo("100");
        assertThat(totals.netAllocatedPayments()).isEqualByComparingTo("0");
        assertThat(totals.unallocatedDeposit()).isEqualByComparingTo("100");
        assertThat(totals.balance()).isEqualByComparingTo("80");
        assertThat(totals.estimatedOutcome()).isEqualTo(SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED);
    }
    @Test void reconcile_appliedDepositAndActualSurplusRefund_balancedWithoutDoubleCounting() {
        var totals = snapshot(List.of(charge("80")), List.of(payment("100", "ADMISSION_DEPOSIT"), returned("20", "REFUND", "ADMISSION_DEPOSIT")),
                List.of(new AllocationLine(pay, charge, amount("80"))), List.of()).reconcile();
        assertThat(totals.completedPayments()).isEqualByComparingTo("100");
        assertThat(totals.completedRefunds()).isEqualByComparingTo("20");
        assertThat(totals.netAllocatedPayments()).isEqualByComparingTo("80");
        assertThat(totals.unallocatedDeposit()).isZero();
        assertThat(totals.unappliedNetPayments()).isZero();
        assertThat(totals.balance()).isZero();
    }
    @Test void reconcile_twoInstallmentsAndPartialRefund_netAllocationUsesOriginal() {
        UUID second = UUID.randomUUID();
        var secondPayment = new TransactionLine(second, account, null, "PAYMENT", "SERVICE_PAYMENT", "COMPLETED", amount("40"), "VND", now.minusSeconds(40));
        var totals = snapshot(List.of(charge("100")), List.of(payment("60", "SERVICE_PAYMENT"), secondPayment, returned("15", "REFUND", "SERVICE_PAYMENT")),
                List.of(new AllocationLine(pay, charge, amount("60")), new AllocationLine(second, charge, amount("40")), new AllocationLine(refund, charge, amount("15"))), List.of()).reconcile();
        assertThat(totals.completedPayments()).isEqualByComparingTo("100");
        assertThat(totals.completedRefunds()).isEqualByComparingTo("15");
        assertThat(totals.netAllocatedPayments()).isEqualByComparingTo("85");
        assertThat(totals.balance()).isEqualByComparingTo("15");
    }
    @Test void reconcile_reversalNotPhysicalRefund_keepsSeparateTotals() {
        var totals = snapshot(List.of(charge("100")), List.of(payment("100", "SERVICE_PAYMENT"), returned("10", "REVERSAL", "SERVICE_PAYMENT")),
                List.of(new AllocationLine(pay, charge, amount("100")), new AllocationLine(refund, charge, amount("10"))), List.of()).reconcile();
        assertThat(totals.completedRefunds()).isZero(); assertThat(totals.completedReversals()).isEqualByComparingTo("10");
        assertThat(totals.netAllocatedPayments()).isEqualByComparingTo("90");
    }
    @Test void reconcile_insuranceApprovalAndReversal_usePersistedOriginalNotCallerAmount() {
        UUID approval = UUID.randomUUID();
        var totals = snapshot(List.of(charge("100")), List.of(payment("75", "SERVICE_PAYMENT")), List.of(new AllocationLine(pay, charge, amount("75"))),
                List.of(new InsuranceLine(approval, account, admission, null, "APPROVAL", amount("40")),
                        new InsuranceLine(UUID.randomUUID(), account, admission, approval, "REVERSAL", amount("15")))).reconcile();
        assertThat(totals.insuranceAmount()).isEqualByComparingTo("25");
        assertThat(totals.patientLiability()).isEqualByComparingTo("75"); assertThat(totals.balance()).isZero();
    }
    @Test void reconcile_voidedCharge_keepsItsUnreturnedCashVisibleWithoutTreatingItAsAllocatedToPostedCharges() {
        var totals = snapshot(List.of(new ChargeLine(charge, account, patient, "VOIDED", amount("100"))),
                List.of(payment("100", "SERVICE_PAYMENT")), List.of(new AllocationLine(pay, charge, amount("100"))), List.of()).reconcile();
        assertThat(totals.grossAmount()).isZero(); assertThat(totals.netAllocatedPayments()).isZero();
        assertThat(totals.unappliedNetPayments()).isEqualByComparingTo("100");
        // An estimated zero allocation balance is not permission to finalize while cash is unapplied.
    }
    @Test void reconcile_pendingAndFailedTransactions_notCompletedCash() {
        var pending = new TransactionLine(pay, account, null, "PAYMENT", "SERVICE_PAYMENT", "PENDING", amount("100"), "VND", null);
        var failed = new TransactionLine(refund, account, pay, "REFUND", "SERVICE_PAYMENT", "FAILED", amount("10"), "VND", null);
        var totals = snapshot(List.of(charge("100")), List.of(pending, failed), List.of(), List.of()).reconcile();
        assertThat(totals.completedPayments()).isZero(); assertThat(totals.completedRefunds()).isZero();
    }
    @Test void reconcile_inputOrderIndependent_refundAndInsuranceReversalMayPrecedeOriginalInRows() {
        UUID approval = UUID.randomUUID();
        var transactions = new ArrayList<>(List.of(payment("100", "SERVICE_PAYMENT"), returned("10", "REFUND", "SERVICE_PAYMENT")));
        var adjustments = new ArrayList<>(List.of(new InsuranceLine(approval, account, admission, null, "APPROVAL", amount("20")),
                new InsuranceLine(UUID.randomUUID(), account, admission, approval, "REVERSAL", amount("10"))));
        var lines = List.of(new AllocationLine(pay, charge, amount("100")), new AllocationLine(refund, charge, amount("10")));
        var before = snapshot(List.of(charge("100")), transactions, lines, adjustments).reconcile();
        Collections.reverse(transactions); Collections.reverse(adjustments);
        assertThat(snapshot(List.of(charge("100")), transactions, lines, adjustments).reconcile()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"foreignCharge", "foreignPatient", "duplicateCharge", "foreignTransaction", "wrongCurrency", "missingOriginal",
            "refundOverOriginal", "refundBeforeOriginal", "futurePayment", "wrongClassification", "unknownType", "duplicateTransaction", "allocationOverPayment",
            "allocationOverCharge", "allocationMissingCharge", "duplicateAllocation", "refundAllocationWrongOriginal", "refundMissingAllocation", "depositOverUnallocated",
            "missingInsuranceOriginal", "insuranceOverOriginal", "insuranceOtherAdmission", "insuranceOverGross", "fractionalCent", "overflow", "sumOverflow"})
    void reconcile_inconsistentLedger_rejectsInsteadOfPlausiblePartialTotal(String mutation) {
        var charges = new ArrayList<>(List.of(charge("100")));
        var transactions = new ArrayList<>(List.of(payment("100", "SERVICE_PAYMENT")));
        var allocations = new ArrayList<>(List.of(new AllocationLine(pay, charge, amount("100"))));
        var insurance = new ArrayList<InsuranceLine>();
        switch (mutation) {
            case "foreignCharge" -> charges.set(0, new ChargeLine(charge, UUID.randomUUID(), patient, "POSTED", amount("100")));
            case "foreignPatient" -> charges.set(0, new ChargeLine(charge, account, UUID.randomUUID(), "POSTED", amount("100")));
            case "duplicateCharge" -> charges.add(charges.getFirst());
            case "foreignTransaction" -> transactions.set(0, new TransactionLine(pay, UUID.randomUUID(), null, "PAYMENT", "SERVICE_PAYMENT", "COMPLETED", amount("100"), "VND", now.minusSeconds(60)));
            case "wrongCurrency" -> transactions.set(0, new TransactionLine(pay, account, null, "PAYMENT", "SERVICE_PAYMENT", "COMPLETED", amount("100"), "USD", now.minusSeconds(60)));
            case "missingOriginal" -> transactions.add(new TransactionLine(refund, account, UUID.randomUUID(), "REFUND", "SERVICE_PAYMENT", "COMPLETED", amount("10"), "VND", now));
            case "refundOverOriginal" -> transactions.add(returned("101", "REFUND", "SERVICE_PAYMENT"));
            case "refundBeforeOriginal" -> transactions.add(new TransactionLine(refund, account, pay, "REFUND", "SERVICE_PAYMENT", "COMPLETED", amount("10"), "VND", now.minusSeconds(100)));
            case "futurePayment" -> transactions.set(0, new TransactionLine(pay, account, null, "PAYMENT", "SERVICE_PAYMENT", "COMPLETED", amount("100"), "VND", now.plusSeconds(1)));
            case "wrongClassification" -> transactions.add(returned("10", "REFUND", "ADMISSION_DEPOSIT"));
            case "unknownType" -> transactions.set(0, new TransactionLine(pay, account, null, null, "SERVICE_PAYMENT", "COMPLETED", amount("100"), "VND", now));
            case "duplicateTransaction" -> transactions.add(transactions.getFirst());
            case "allocationOverPayment" -> { charges.set(0, charge("200")); allocations.set(0, new AllocationLine(pay, charge, amount("101"))); }
            case "allocationOverCharge" -> charges.set(0, charge("99"));
            case "allocationMissingCharge" -> allocations.set(0, new AllocationLine(pay, UUID.randomUUID(), amount("100")));
            case "duplicateAllocation" -> allocations.add(allocations.getFirst());
            case "refundAllocationWrongOriginal" -> { UUID other = UUID.randomUUID(); charges.add(new ChargeLine(other, account, patient, "POSTED", amount("100"))); transactions.add(returned("10", "REFUND", "SERVICE_PAYMENT")); allocations.add(new AllocationLine(refund, other, amount("10"))); }
            case "refundMissingAllocation" -> transactions.add(returned("10", "REFUND", "SERVICE_PAYMENT"));
            case "depositOverUnallocated" -> { transactions.set(0, payment("100", "ADMISSION_DEPOSIT")); transactions.add(returned("30", "REFUND", "ADMISSION_DEPOSIT")); allocations.set(0, new AllocationLine(pay, charge, amount("80"))); transactions.add(new TransactionLine(UUID.randomUUID(), account, null, "PAYMENT", "ADMISSION_DEPOSIT", "COMPLETED", amount("100"), "VND", now)); }
            case "missingInsuranceOriginal" -> insurance.add(new InsuranceLine(UUID.randomUUID(), account, admission, UUID.randomUUID(), "REVERSAL", amount("10")));
            case "insuranceOverOriginal" -> { UUID id = UUID.randomUUID(); insurance.add(new InsuranceLine(id, account, admission, null, "APPROVAL", amount("10"))); insurance.add(new InsuranceLine(UUID.randomUUID(), account, admission, id, "REVERSAL", amount("11"))); }
            case "insuranceOtherAdmission" -> insurance.add(new InsuranceLine(UUID.randomUUID(), account, UUID.randomUUID(), null, "APPROVAL", amount("10")));
            case "insuranceOverGross" -> insurance.add(new InsuranceLine(UUID.randomUUID(), account, admission, null, "APPROVAL", amount("101")));
            case "fractionalCent" -> charges.set(0, charge("100.001"));
            case "overflow" -> charges.set(0, charge("100000000000000000.00"));
            case "sumOverflow" -> { charges.set(0, charge("99999999999999999.00")); charges.add(new ChargeLine(UUID.randomUUID(), account, patient, "POSTED", amount("99999999999999999.00"))); }
        }
        assertThatThrownBy(() -> snapshot(charges, transactions, allocations, insurance).reconcile())
                .isInstanceOf(BillingRuleException.class).hasMessage("Admission ledger evidence is inconsistent");
    }
    @Test void reconcile_openAccount_notASettlementEvenIfMoneyMatches() {
        var snapshot = new AdmissionLedgerSnapshot(account, admission, patient, department, "VND", AccountStatus.OPEN,
                null, now, List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(snapshot::reconcile).isInstanceOf(BillingRuleException.class).hasMessage("Admission charges are not closed");
    }
    @Test void reconcile_approvedInsuranceMakesOverpayment_estimatesRefundDueNotAutomaticRefund() {
        var totals = snapshot(List.of(charge("100")), List.of(payment("100", "SERVICE_PAYMENT")), List.of(new AllocationLine(pay, charge, amount("100"))),
                List.of(new InsuranceLine(UUID.randomUUID(), account, admission, null, "APPROVAL", amount("20")))).reconcile();
        assertThat(totals.balance()).isEqualByComparingTo("-20"); assertThat(totals.estimatedOutcome()).isEqualTo(SettlementOutcome.REFUND_DUE);
    }
}
