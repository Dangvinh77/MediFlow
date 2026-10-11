package com.mediflow.billing.domain.model;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionDepositAllocationTest {
    private final UUID account=UUID.randomUUID(), admission=UUID.randomUUID(), patient=UUID.randomUUID(),
            department=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID(), original=UUID.randomUUID();
    private final Instant time=Instant.parse("2026-10-10T03:00:00Z");
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private AdmissionLedgerSnapshot snapshot(List<AdmissionLedgerSnapshot.TransactionLine> transactions,
            List<AdmissionLedgerSnapshot.AllocationLine> allocations) {
        return new AdmissionLedgerSnapshot(account,admission,patient,department,"VND",AccountStatus.CHARGE_CLOSED,
                time.minusSeconds(1),time,List.of(
                    new AdmissionLedgerSnapshot.ChargeLine(first,account,patient,ChargeStatus.POSTED,money("48")),
                    new AdmissionLedgerSnapshot.ChargeLine(second,account,patient,ChargeStatus.POSTED,money("32"))),
                transactions,allocations,List.of());
    }
    private AdmissionLedgerSnapshot.TransactionLine deposit() {
        return new AdmissionLedgerSnapshot.TransactionLine(original,account,null,PaymentTransactionType.PAYMENT,
                PaymentClassification.ADMISSION_DEPOSIT,PaymentTransactionStatus.COMPLETED,money("100"),"VND",time.minusSeconds(60));
    }
    private AdmissionLedgerSnapshot.TransactionLine refund(UUID id, String amount) {
        return new AdmissionLedgerSnapshot.TransactionLine(id,account,original,PaymentTransactionType.REFUND,
                PaymentClassification.ADMISSION_DEPOSIT,PaymentTransactionStatus.COMPLETED,money(amount),"VND",time.minusSeconds(30));
    }
    @Test void allocateOriginal100AfterActualExcessRefund20ToExact48And32Charges() {
        var ledger=snapshot(List.of(deposit(),refund(UUID.randomUUID(),"20")),List.of());
        var allocation=AdmissionDepositAllocation.plan(ledger,original,Map.of(first,money("48"),second,money("32")));
        assertThat(allocation.amount()).isEqualByComparingTo("80");
        assertThat(allocation.lines()).hasSize(2); assertThat(allocation.originalTransactionId()).isEqualTo(original);
        // This is allocation math, not a final settlement or an earned contribution.
        assertThat(ledger.reconcile().unallocatedDeposit()).isEqualByComparingTo("80");
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","100000000000000000"})
    void invalidExactMoneyRejectedWithoutRounding(String value) {
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(snapshot(List.of(deposit()),List.of()),original,Map.of(first,money(value))))
                .isInstanceOf(BillingRuleException.class);
    }
    @Test void cannotAllocateOriginalFundsAlreadyReturned() {
        var ledger=snapshot(List.of(deposit(),refund(UUID.randomUUID(),"60")),List.of());
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(ledger,original,Map.of(first,money("48"))))
                .isInstanceOf(BillingRuleException.class);
    }
    @Test void cannotBorrowAnotherOriginalDepositBudget() {
        var other=new AdmissionLedgerSnapshot.TransactionLine(UUID.randomUUID(),account,null,PaymentTransactionType.PAYMENT,
                PaymentClassification.ADMISSION_DEPOSIT,PaymentTransactionStatus.COMPLETED,money("200"),"VND",time.minusSeconds(60));
        var ledger=snapshot(List.of(deposit(),other,refund(UUID.randomUUID(),"80")),List.of());
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(ledger,original,Map.of(first,money("48"))))
                .isInstanceOf(BillingRuleException.class);
    }
    @Test void cannotAllocateToForeignOrOverfundedCharge() {
        var ledger=snapshot(List.of(deposit()),List.of());
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(ledger,original,Map.of(UUID.randomUUID(),money("1"))))
                .isInstanceOf(BillingRuleException.class);
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(ledger,original,Map.of(first,money("49"))))
                .isInstanceOf(BillingRuleException.class);
    }
    @Test void neverOverwritesExistingOriginalChargeAllocation() {
        var ledger=snapshot(List.of(deposit()),List.of(new AdmissionLedgerSnapshot.AllocationLine(original,first,money("10"))));
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(ledger,original,Map.of(first,money("20"))))
                .isInstanceOf(BillingRuleException.class).hasMessage("Append-only allocation already exists for this original/charge");
    }
    @Test void linkedAllocatedRefundRestoresChargeBudgetButDoesNotInventNewOriginalCash() {
        UUID refund=UUID.randomUUID();
        var ledger=snapshot(List.of(deposit(),refund(refund,"10")),List.of(
                new AdmissionLedgerSnapshot.AllocationLine(original,first,money("40")),
                new AdmissionLedgerSnapshot.AllocationLine(refund,first,money("10"))));
        var planned=AdmissionDepositAllocation.plan(ledger,original,Map.of(second,money("32")));
        assertThat(planned.amount()).isEqualByComparingTo("32");
    }
    @Test void servicePaymentCannotBeReclassifiedAsAdmissionDeposit() {
        var payment=new AdmissionLedgerSnapshot.TransactionLine(original,account,null,PaymentTransactionType.PAYMENT,
                PaymentClassification.SERVICE_PAYMENT,PaymentTransactionStatus.COMPLETED,money("100"),"VND",time.minusSeconds(60));
        assertThatThrownBy(() -> AdmissionDepositAllocation.plan(snapshot(List.of(payment),List.of()),original,Map.of(first,money("48"))))
                .isInstanceOf(BillingRuleException.class);
    }
}
