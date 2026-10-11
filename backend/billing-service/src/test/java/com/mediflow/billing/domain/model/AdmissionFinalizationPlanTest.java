package com.mediflow.billing.domain.model;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AdmissionFinalizationPlanTest {
    private final UUID account=UUID.randomUUID(),admission=UUID.randomUUID(),patient=UUID.randomUUID(),accountDepartment=UUID.randomUUID();
    private final UUID first=UUID.randomUUID(),second=UUID.randomUUID(),firstDepartment=UUID.randomUUID(),secondDepartment=UUID.randomUUID();
    private final UUID deposit=UUID.randomUUID(),refund=UUID.randomUUID();
    private final Instant medical=Instant.parse("2026-10-10T10:00:00Z"),closed=medical.plusSeconds(1),now=closed.plusSeconds(1);
    private AdmissionLedgerSnapshot snapshot(String refundAmount,String firstAllocation,AccountStatus status) {
        return new AdmissionLedgerSnapshot(account,admission,patient,accountDepartment,"VND",status,closed,now,
                List.of(new AdmissionLedgerSnapshot.ChargeLine(first,account,patient,ChargeStatus.POSTED,money("60")),
                        new AdmissionLedgerSnapshot.ChargeLine(second,account,patient,ChargeStatus.POSTED,money("40"))),
                List.of(new AdmissionLedgerSnapshot.TransactionLine(deposit,account,null,PaymentTransactionType.PAYMENT,PaymentClassification.ADMISSION_DEPOSIT,
                                PaymentTransactionStatus.COMPLETED,money("120"),"VND",medical.minusSeconds(10)),
                        new AdmissionLedgerSnapshot.TransactionLine(refund,account,deposit,PaymentTransactionType.REFUND,PaymentClassification.ADMISSION_DEPOSIT,
                                PaymentTransactionStatus.COMPLETED,money(refundAmount),"VND",medical.minusSeconds(1))),
                List.of(new AdmissionLedgerSnapshot.AllocationLine(deposit,first,money(firstAllocation)),
                        new AdmissionLedgerSnapshot.AllocationLine(deposit,second,money("40"))),List.of());
    }
    private List<AdmissionFinalizationPlan.PerformedCharge> proofs() {
        return List.of(proof(first,firstDepartment),proof(second,secondDepartment));
    }
    private AdmissionFinalizationPlan.PerformedCharge proof(UUID charge,UUID department) {
        return new AdmissionFinalizationPlan.PerformedCharge(charge,department,"SURGERY",UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),medical.minusSeconds(5));
    }
    @Test void paidFinalizationUsesGeneratingDepartmentsNotAccountAndReleasesOnlyAppliedOriginalDeposit() {
        var ready=AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.CHARGE_CLOSED),medical,proofs());
        assertThat(ready.earnedByDepartment()).containsOnlyKeys(firstDepartment,secondDepartment);
        assertThat(ready.earnedByDepartment().get(firstDepartment)).isEqualByComparingTo("60");
        assertThat(ready.earnedByDepartment().get(secondDepartment)).isEqualByComparingTo("40");
        assertThat(ready.depositReleaseByOriginal().get(deposit)).isEqualByComparingTo("100");
        assertThat(ready.totals().balance()).isEqualByComparingTo("0");
    }
    @Test void zeroBalanceWithUnusedCashCannotFinalizeUntilActualRefund() {
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("19","60",AccountStatus.CHARGE_CLOSED),medical,proofs()))
                .hasMessage("BILLING_FINAL_SETTLEMENT_MONEY_UNRESOLVED");
    }
    @Test void pendingPaymentDoesNotBecomeDebtApprovalOrRecognition() {
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","59",AccountStatus.CHARGE_CLOSED),medical,proofs()))
                .hasMessage("BILLING_FINAL_SETTLEMENT_MONEY_UNRESOLVED");
    }
    @ParameterizedTest @ValueSource(strings={"OPEN","SETTLED","CLOSED"})
    void nonCloseableAccountCannotFinalizeAgain(String status) {
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.valueOf(status)),medical,proofs()))
                .hasMessage("BILLING_FINAL_SETTLEMENT_NOT_READY");
    }
    @Test void missingPerformedProofCannotRecognizePlannedCharges() {
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.CHARGE_CLOSED),medical,List.of(proofs().getFirst())))
                .hasMessage("BILLING_FINAL_PERFORMED_PROOF_INCOMPLETE");
    }
    @Test void duplicateProofCannotHideMissingCharge() {
        var proof=proofs().getFirst();
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.CHARGE_CLOSED),medical,List.of(proof,proof)))
                .hasMessage("BILLING_FINAL_PERFORMED_PROOF_INVALID");
    }
    @Test void unknownChargeCannotReplaceMissingProof() {
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.CHARGE_CLOSED),medical,
                List.of(proofs().getFirst(),proof(UUID.randomUUID(),secondDepartment))))
                .hasMessage("BILLING_FINAL_PERFORMED_PROOF_INCOMPLETE");
    }
    @Test void performanceAfterMedicalApprovalRequiresReopenNotBackdatedRecognition() {
        var late=new AdmissionFinalizationPlan.PerformedCharge(second,secondDepartment,"SURGERY",UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),medical.plusSeconds(1));
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.CHARGE_CLOSED),medical,List.of(proofs().getFirst(),late)))
                .hasMessage("BILLING_FINAL_PERFORMED_PROOF_INVALID");
    }
    @Test void futureMedicalProofDoesNotFinalize() {
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(snapshot("20","60",AccountStatus.CHARGE_CLOSED),now.plusSeconds(1),proofs()))
                .hasMessage("BILLING_FINAL_SETTLEMENT_NOT_READY");
    }
    @Test void completedReversalNeedsExplicitFinalConsumerContractRatherThanAmbiguousV1Totals() {
        var original=snapshot("20","60",AccountStatus.CHARGE_CLOSED);var returned=original.transactions().get(1);
        var reversal=new AdmissionLedgerSnapshot.TransactionLine(returned.id(),returned.accountId(),returned.originalId(),PaymentTransactionType.REVERSAL,
                returned.classification(),returned.status(),returned.amount(),returned.currency(),returned.completedAt());
        var revised=new AdmissionLedgerSnapshot(original.accountId(),original.admissionId(),original.patientId(),original.departmentId(),original.currency(),
                original.accountStatus(),original.chargeClosedAt(),original.observedAt(),original.charges(),List.of(original.transactions().getFirst(),reversal),
                original.allocations(),original.insurance());
        assertThatThrownBy(()->AdmissionFinalizationPlan.prepare(revised,medical,proofs())).hasMessage("BILLING_FINAL_REVERSAL_CONTRACT_UNSUPPORTED");
    }
    private static BigDecimal money(String amount){return new BigDecimal(amount);}
}
