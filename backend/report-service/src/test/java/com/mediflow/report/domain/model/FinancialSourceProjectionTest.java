package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Pure arithmetic scenarios, not producer fixtures or source-coverage approval. */
class FinancialSourceProjectionTest {
    private static final String ZONE="Asia/Bangkok";
    private static final LocalDate FROM=LocalDate.of(2026,10,9),TO=LocalDate.of(2026,10,11);
    @Test void receiptRefundFinalOnDifferentDaysKeepFlowsAndClosingStocksSeparate() {
        var result=rebuild(List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(finalFact()),List.of(balance(1),balance(2)));
        assertThat(hospital(result,FROM)).isEqualTo(amounts("120","120","0","0","100"));
        assertThat(hospital(result,FROM.plusDays(1))).isEqualTo(amounts("0","100","0","20","0"));
        assertThat(hospital(result,TO)).isEqualTo(amounts("0","0","100","0","0"));
        assertThat(day(result,TO,id(9)).earnedRevenue()).isEqualByComparingTo("60");assertThat(day(result,TO,id(10)).earnedRevenue()).isEqualByComparingTo("40");
        assertThat(day(result,TO,id(4)).earnedRevenue()).isEqualByComparingTo("0");
        var month=hospital(result,FROM).through(hospital(result,FROM.plusDays(1))).through(hospital(result,TO));
        assertThat(month).isEqualTo(amounts("120","0","100","20","0"));
    }
    @Test void shuffledAndExactRepeatedSourcesHaveIdenticalDeterministicRows() {
        var expected=rebuild(List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(finalFact()),List.of(balance(1),balance(2)));
        assertThat(rebuild(List.of(receipt(id(11),money("120"),id(4)),receipt(id(11),money("120"),id(4))),List.of(refund(),refund()),
                List.of(finalFact(),finalFact()),List.of(balance(2),balance(1),balance(2)))).isEqualTo(expected);
    }
    @Test void earlierOriginalIsCarriedAsOpeningStockNotAnotherReceiptFlow() {
        var rows=FinancialSourceProjection.rebuild(FROM.plusDays(1),TO,ZONE,List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(finalFact()),List.of(balance(1),balance(2)));
        assertThat(hospital(rows,FROM.plusDays(1))).isEqualTo(amounts("0","100","0","20","0"));
    }
    @Test void latestNonFinalZeroAndFinalZeroDoNotAddAnotherCashOrRevenueContribution() {
        var rows=rebuild(List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(),List.of(balance(2),balance(1)));
        assertThat(hospital(rows,TO)).isEqualTo(amounts("0","100","0","0","0"));
    }
    @Test void twoPaymentTransactionsOnSameRequestAreTwoInflowsNotOneInvoice() {
        var rows=rebuild(List.of(receipt(id(11),money("120"),id(4)),receipt(id(50),money("120"),id(4))),List.of(),List.of(),List.of());
        assertThat(hospital(rows,FROM)).isEqualTo(amounts("240","240","0","0","0"));
    }
    @Test void finalOperationOverridesLastNonFinalDebtEvenWhenFinalArrivesFirst() {
        var rows=rebuild(List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(finalFact()),List.of(balance(1)));
        assertThat(hospital(rows,FROM).outstandingReceivable()).isEqualByComparingTo("100");assertThat(hospital(rows,TO).outstandingReceivable()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"missingOriginal","missingRefund","changedOriginal","missingRevision","forkRevision","futureNonFinal","missingFinalCash"})
    void incompleteOrConflictingFinancialSourcesCannotBeDeclaredComplete(String scenario) {
        var receipts=new ArrayList<>(List.of(receipt(id(11),money("120"),id(4))));var refunds=new ArrayList<>(List.of(refund()));
        var finals=new ArrayList<>(List.of(finalFact()));var balances=new ArrayList<>(List.of(balance(1),balance(2)));
        switch(scenario) {
            case "missingOriginal"->receipts.clear();case "missingRefund"->refunds.clear();
            case "changedOriginal"->receipts.add(receipt(id(11),money("121"),id(4)));
            case "missingRevision"->balances.removeFirst();
            case "forkRevision"->{var first=balance(1);balances.add(new SettlementReceivableSnapshot(id(51),1,null,first.accountId(),first.admissionId(),first.patientId(),first.departmentId(),"VND",
                    first.grossAmount(),first.insuranceAmount(),first.patientLiability(),first.completedPayments(),first.completedRefunds(),first.completedReversals(),first.netAllocatedPayments(),first.balance(),
                    first.estimatedOutcome(),first.reconciledAt(),false,ZONE));}
            case "futureNonFinal"->{var first=balance(1);balances.set(0,new SettlementReceivableSnapshot(first.settlementId(),1,null,first.accountId(),first.admissionId(),first.patientId(),first.departmentId(),"VND",
                    first.grossAmount(),first.insuranceAmount(),first.patientLiability(),first.completedPayments(),first.completedRefunds(),first.completedReversals(),first.netAllocatedPayments(),first.balance(),
                    first.estimatedOutcome(),Instant.parse("2026-10-12T00:00:00Z"),false,ZONE));}
            case "missingFinalCash"->{receipts.clear();refunds.clear();}
            default->throw new IllegalArgumentException();
        }
        assertThatThrownBy(()->rebuild(receipts,refunds,finals,balances)).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @Test void hospitalReconcilesEveryMetricAgainstDepartmentsForEveryDay() {
        var rows=rebuild(List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(finalFact()),List.of(balance(1),balance(2)));
        for(var date:List.of(FROM,FROM.plusDays(1),TO)) {
            var scopes=rows.stream().filter(row->row.businessDate().equals(date)&&row.departmentId()!=null).map(FinancialSourceProjection.Day::amounts).toList();
            var h=hospital(rows,date);assertThat(scopes.stream().map(FinancialAmounts::cashReceived).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(h.cashReceived());
            assertThat(scopes.stream().map(FinancialAmounts::depositLiability).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(h.depositLiability());
            assertThat(scopes.stream().map(FinancialAmounts::earnedRevenue).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(h.earnedRevenue());
            assertThat(scopes.stream().map(FinancialAmounts::refunds).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(h.refunds());
            assertThat(scopes.stream().map(FinancialAmounts::outstandingReceivable).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(h.outstandingReceivable());
        }
    }
    @Test void oversizedHistoryAndDateRangeRejectBeforeBuildingAnUnboundedSnapshot() {
        assertThatThrownBy(()->rebuild(Collections.nCopies(10_001,receipt(id(11),money("120"),id(4))),List.of(),List.of(),List.of())).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        assertThatThrownBy(()->FinancialSourceProjection.rebuild(FROM,FROM.plusDays(366),ZONE,List.of(),List.of(),List.of(),List.of())).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @Test void futureBusinessFactCannotBeHiddenOutsideRequestedRebuildRange() {
        var manifest=new FinancialSourceManifest(List.of(receipt(id(11),money("120"),id(4))),List.of(refund()),List.of(finalFact()),List.of());
        assertThatThrownBy(()->manifest.project(FROM,FROM,ZONE,Instant.parse("2026-10-10T23:00:00Z")))
                .isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @Test void manifestDefensivelyCopiesInputAndRejectsInvalidZone() {
        var inputs=new ArrayList<>(List.of(receipt(id(11),money("120"),id(4))));
        var manifest=new FinancialSourceManifest(inputs,List.of(),List.of(),List.of());inputs.clear();
        assertThat(manifest.size()).isOne(); assertThatThrownBy(()->manifest.receipts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->FinancialSourceProjection.rebuild(FROM,TO,"bad-zone",List.of(),List.of(),List.of(),List.of()))
                .isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @ParameterizedTest @ValueSource(strings={"department","patient"})
    void cashAndNonFinalStockCannotCombineConflictingAccountContextWithoutAFinalFact(String mismatch) {
        var original=balance(1);
        var changed=new SettlementReceivableSnapshot(original.settlementId(),1,null,original.accountId(),original.admissionId(),
                "patient".equals(mismatch)?id(61):original.patientId(),"department".equals(mismatch)?id(62):original.departmentId(),"VND",
                original.grossAmount(),original.insuranceAmount(),original.patientLiability(),original.completedPayments(),original.completedRefunds(),
                original.completedReversals(),original.netAllocatedPayments(),original.balance(),original.estimatedOutcome(),original.reconciledAt(),false,ZONE);
        assertThatThrownBy(()->rebuild(List.of(receipt(id(11),money("120"),id(4))),List.of(),List.of(),List.of(changed)))
                .isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void serviceFundingWithoutDepositRelease_requiresExactReceiptDepartment(boolean sameDepartment) {
        var original = finalFact();
        var finalService = new FinalSettlementRecognition(original.recognitionOperationId(),1,null,original.settlementId(),1,null,
                original.accountId(),original.admissionId(),original.patientId(),original.accountDepartmentId(),"VND",original.totals(),
                original.completedAt(),original.businessDate(),ZONE,true,"PAID_IN_FULL",original.charges(),
                original.allocations().stream().map(line -> new FinalSettlementRecognition.Allocation(line.allocationId(),line.transactionId(),
                        line.originalTransactionId(),line.chargeId(),line.transactionType(),"SERVICE_PAYMENT",line.amount())).toList(),List.of());
        UUID department = sameDepartment ? id(4) : id(63);
        var funding = new CashReceipt(id(11),null,id(31),id(1),id(3),department,"ADMISSION",id(2),CashReceipt.Classification.SERVICE_PAYMENT,
                money("120"),"VND","CASH",Instant.parse("2026-10-09T08:00:00Z"),FROM,ZONE);
        var returned = new CashRefund(id(12),id(11),id(1),id(3),department,"ADMISSION",id(2),money("20"),"VND",
                Instant.parse("2026-10-10T08:00:00Z"),FROM.plusDays(1),ZONE);
        if (sameDepartment) assertThat(finalService.verifyFunding(List.of(funding),List.of(returned))).isTrue();
        else assertThatThrownBy(() -> finalService.verifyFunding(List.of(funding),List.of(returned)))
                .isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    private static List<FinancialSourceProjection.Day> rebuild(List<CashReceipt> receipts,List<CashRefund> refunds,List<FinalSettlementRecognition> finals,List<SettlementReceivableSnapshot> balances){return FinancialSourceProjection.rebuild(FROM,TO,ZONE,receipts,refunds,finals,balances);}
    private static FinancialAmounts hospital(List<FinancialSourceProjection.Day> rows,LocalDate date){return day(rows,date,null);}
    private static FinancialAmounts day(List<FinancialSourceProjection.Day> rows,LocalDate date,UUID department){return rows.stream().filter(row->row.businessDate().equals(date)&&java.util.Objects.equals(row.departmentId(),department)).findFirst().orElseThrow().amounts();}
    private static FinancialAmounts amounts(String cash,String held,String earned,String refunds,String owed){return new FinancialAmounts(money(cash),money(held),money(earned),money(refunds),money(owed));}
    private static CashReceipt receipt(UUID transaction,BigDecimal amount,UUID department){return new CashReceipt(transaction,null,id(31),id(1),id(3),department,"ADMISSION",id(2),CashReceipt.Classification.ADMISSION_DEPOSIT,amount,"VND","CASH",Instant.parse("2026-10-09T08:00:00Z"),FROM,ZONE);}
    private static CashRefund refund(){return new CashRefund(id(12),id(11),id(1),id(3),id(4),"ADMISSION",id(2),money("20"),"VND",Instant.parse("2026-10-10T08:00:00Z"),FROM.plusDays(1),ZONE);}
    private static FinalSettlementRecognition finalFact(){return new FinalSettlementRecognition(id(15),1,null,id(16),1,null,id(1),id(2),id(3),id(4),"VND",
            new FinalSettlementRecognition.Totals(money("100"),money("0"),money("100"),money("120"),money("20"),money("0"),money("100"),money("0")),
            Instant.parse("2026-10-11T08:00:00Z"),TO,ZONE,true,"PAID_IN_FULL",List.of(
                new FinalSettlementRecognition.Charge(id(5),id(9),"SURGERY",id(7),id(19),"a".repeat(64),Instant.parse("2026-10-09T07:00:00Z"),money("60")),
                new FinalSettlementRecognition.Charge(id(6),id(10),"SURGERY",id(8),id(20),"b".repeat(64),Instant.parse("2026-10-09T07:00:00Z"),money("40"))),
            List.of(new FinalSettlementRecognition.Allocation(id(13),id(11),null,id(5),"PAYMENT","ADMISSION_DEPOSIT",money("60")),new FinalSettlementRecognition.Allocation(id(14),id(11),null,id(6),"PAYMENT","ADMISSION_DEPOSIT",money("40"))),
            List.of(new FinalSettlementRecognition.DepositRelease(id(11),id(4),money("100"))));}
    private static SettlementReceivableSnapshot balance(int version){return new SettlementReceivableSnapshot(id(20+version),version,version==1?null:id(21),id(1),id(2),id(3),id(4),"VND",
            money("100"),money("0"),money("100"),money(version==1?"0":"120"),money(version==1?"0":"20"),money("0"),money(version==1?"0":"100"),money(version==1?"100":"0"),
            version==1?SettlementReceivableSnapshot.Outcome.ADDITIONAL_PAYMENT_REQUIRED:SettlementReceivableSnapshot.Outcome.PAID_IN_FULL,
            Instant.parse(version==1?"2026-10-09T10:00:00Z":"2026-10-10T10:00:00Z"),false,ZONE);}
    private static BigDecimal money(String amount){return new BigDecimal(amount).setScale(2);}
    private static UUID id(int value){return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",value));}
}
