package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.mediflow.report.domain.exception.ReportRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SettlementReceivableSnapshotTest {
    private static final UUID FIRST=UUID.randomUUID(), SECOND=UUID.randomUUID(), ACCOUNT=UUID.randomUUID(),
            ADMISSION=UUID.randomUUID(), PATIENT=UUID.randomUUID(), DEPARTMENT=UUID.randomUUID();
    private static final Instant TIME=Instant.parse("2026-10-10T10:00:00.123456789Z");
    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private SettlementReceivableSnapshot snapshot(UUID id,int revision,UUID previous,Instant at,String currency,String insurance,String allocated) {
        BigDecimal liability=amount("100").subtract(amount(insurance)), balance=liability.subtract(amount(allocated));
        var outcome=balance.signum()>0?SettlementReceivableSnapshot.Outcome.ADDITIONAL_PAYMENT_REQUIRED
                :balance.signum()<0?SettlementReceivableSnapshot.Outcome.REFUND_DUE:SettlementReceivableSnapshot.Outcome.PAID_IN_FULL;
        return new SettlementReceivableSnapshot(id,revision,previous,ACCOUNT,ADMISSION,PATIENT,DEPARTMENT,currency,
                amount("100"),amount(insurance),liability,amount("100"),amount("15"),amount("0"),amount(allocated),balance,outcome,at,false,"Asia/Bangkok");
    }
    @Test void receivable_validPositiveBalance_isStockNotCompletedCash() {
        assertThat(snapshot(FIRST,1,null,TIME,"VND","0","85").receivable()).isEqualByComparingTo("15");
    }
    @Test void receivable_insuranceCreatesRefundDue_isZeroNotNegative() {
        var s=snapshot(FIRST,1,null,TIME,"VND","20","85");
        assertThat(s.balance()).isEqualByComparingTo("-5"); assertThat(s.receivable()).isZero();
        assertThat(s.completedPayments()).isEqualByComparingTo("100");
    }
    @Test void successor_nextRevision_preservesExactNanosAndContext() {
        var first=snapshot(FIRST,1,null,TIME,"VND","0","85");
        var second=snapshot(SECOND,2,FIRST,TIME.plusNanos(1),"VND","20","85");
        assertThatCode(()->first.requireSuccessor(second)).doesNotThrowAnyException();
    }
    @Test void successor_businessTimeMovesBackward_rejects() {
        var first=snapshot(FIRST,1,null,TIME,"VND","0","85");
        var next=snapshot(SECOND,2,FIRST,TIME.minusNanos(1),"VND","0","85");
        assertThatThrownBy(()->first.requireSuccessor(next)).isInstanceOf(ReportRuleException.class);
    }
    @Test void successor_wrongPredecessor_rejects() {
        var first=snapshot(FIRST,1,null,TIME,"VND","0","85");
        var next=snapshot(SECOND,2,UUID.randomUUID(),TIME,"VND","0","85");
        assertThatThrownBy(()->first.requireSuccessor(next)).isInstanceOf(ReportRuleException.class);
    }
    @Test void successor_changedCurrency_isNotASecondAllocation() {
        var first=snapshot(FIRST,1,null,TIME,"VND","0","85");
        var next=snapshot(SECOND,2,FIRST,TIME,"USD","0","85");
        assertThatThrownBy(()->first.requireSuccessor(next)).isInstanceOf(ReportRuleException.class);
    }
    @ParameterizedTest @ValueSource(strings={"vnd","VN","USDD","","V N"})
    void create_invalidCurrency_rejects(String currency) {
        assertThatThrownBy(()->snapshot(FIRST,1,null,TIME,currency,"0","85")).isInstanceOf(ReportRuleException.class);
    }
    @Test void create_revisionOneCannotSupersede_rejects() {
        assertThatThrownBy(()->snapshot(FIRST,1,SECOND,TIME,"VND","0","85")).isInstanceOf(ReportRuleException.class);
    }
    @Test void create_laterRevisionRequiresPredecessor_rejects() {
        assertThatThrownBy(()->snapshot(SECOND,2,null,TIME,"VND","0","85")).isInstanceOf(ReportRuleException.class);
    }
    @Test void create_allocatedExceedsNetCash_rejects() {
        assertThatThrownBy(()->snapshot(FIRST,1,null,TIME,"VND","0","86")).isInstanceOf(ReportRuleException.class);
    }
}
