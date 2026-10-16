package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import com.mediflow.report.domain.model.OutpatientCoverage;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OutpatientReportReadServiceTest {
    private final OutpatientReportReadPort store=mock(OutpatientReportReadPort.class);
    private final LocalDate date=LocalDate.of(2026,10,1);
    private final ZoneId zone=ZoneId.of("Asia/Bangkok");
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-14T01:00:00Z"),zone);
    private final OutpatientReportReadService service=new OutpatientReportReadService(store,zone,clock);
    @Test void missingCoverageIsUnavailableNotZero(){
        when(store.load(date,date,null,zone.getId(),"VND")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.daily(date,date,null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @Test void acceptedCompleteEmptyPeriodCanReturnRealZero(){
        stub(proof(date,date,null),"a".repeat(64),false,List.of());
        var report=service.daily(date,date,null);
        assertThat(report.covered()).isTrue();assertThat(report.periods()).hasSize(1);
        assertThat(report.periods().getFirst().completedVisits()).isZero();
        assertThat(report.periods().getFirst().cashReceived()).isEqualByComparingTo("0.00");
    }
    @Test void partialRefundUsesItsActualDayAndCanMakeDayNetCashNegative(){
        stub(proof(date,date,null),"a".repeat(64),false,List.of(day(0,"0.00","20.00")));
        var p=service.daily(date,date,null).periods().getFirst();
        assertThat(p.cashReceived()).isZero();assertThat(p.refunds()).isEqualByComparingTo("20.00");
        assertThat(p.netCash()).isEqualByComparingTo("-20.00");
    }
    @Test void changedOrLateEvidenceInvalidatesAcceptanceRatherThanPublishStaleTotals(){
        stub(proof(date,date,null),"b".repeat(64),false,List.of(day(1,"100.00","0.00")));
        assertUnavailable();
    }
    @Test void pendingEarlyRefundIsNotKnownZero(){
        stub(proof(date,date,null),"a".repeat(64),true,List.of());assertUnavailable();
    }
    @Test void duplicateDateAndNegativeCountsCannotBePublished(){
        stub(proof(date,date,null),"a".repeat(64),false,List.of(day(1,"10.00","0.00"),day(1,"10.00","0.00")));
        assertUnavailable();
        stub(proof(date,date,null),"a".repeat(64),false,List.of(day(-1,"10.00","0.00")));assertUnavailable();
    }
    @Test void wrongDepartmentOrZoneProofDoesNotAuthorizeAnotherScope(){
        stub(proof(date,date,UUID.randomUUID()),"a".repeat(64),false,List.of());assertUnavailable();
    }
    @Test void invalidRangeIsRejectedBeforeAnySourceRead(){
        assertThatThrownBy(()->service.daily(date,date.minusDays(1),null)).isInstanceOf(ReportPeriodValidationException.class);
        assertThatThrownBy(()->service.monthly(1999,null)).isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store);
    }
    @Test void anotherZoneOrFutureAcceptanceDoesNotAuthorizeNumbers(){
        var p=proof(date,date,null);
        var wrongZone=new OutpatientCoverage(p.coverageId(),date,date,null,"UTC","VND",p.sourceReferences(),
                p.sourceObservedAt(),p.reconciledAt(),p.acceptedAt(),p.acceptedBy(),p.acceptanceReference(),p.evidenceFingerprint());
        stub(wrongZone,"a".repeat(64),false,List.of());assertUnavailable();
        var future=new OutpatientCoverage(p.coverageId(),date,date,null,zone.getId(),"VND",p.sourceReferences(),
                p.sourceObservedAt(),p.reconciledAt(),clock.instant().plusSeconds(60),p.acceptedBy(),p.acceptanceReference(),p.evidenceFingerprint());
        stub(future,"a".repeat(64),false,List.of());assertUnavailable();
    }
    @Test void fractionsAndOverflowNeverBecomeRoundedReportValues(){
        stub(proof(date,date,null),"a".repeat(64),false,List.of(day(1,"10.001","0.00")));assertUnavailable();
        stub(proof(date,date,null),"a".repeat(64),false,List.of(day(1,"100000000000000000.00","0.00")));assertUnavailable();
    }
    @Test void monthlyReaderUsesFiniteYearCoverageNotSettlementPublication(){
        LocalDate from=LocalDate.of(2025,1,1),to=LocalDate.of(2025,12,31);
        var proof=proof(from,to,null);
        var days=List.of(new OutpatientReportReadPort.Day(LocalDate.of(2025,2,1),1,2,3,4,new BigDecimal("100.00"),new BigDecimal("20.00")));
        when(store.load(from,to,null,zone.getId(),"VND")).thenReturn(Optional.of(new OutpatientReportReadPort.Evidence(proof,"a".repeat(64),false,days)));
        var report=service.monthly(2025,null);assertThat(report.periods()).hasSize(12);
        assertThat(report.periods().get(1).netCash()).isEqualByComparingTo("80.00");
        assertThat(report.periods().get(1).dispensedUnits()).isEqualTo(4);
    }
    @Test void incompleteSourcesOrPrematurePeriodCoverageCannotBeConstructed(){
        var p=proof(date,date,null);
        assertThatThrownBy(()->new OutpatientCoverage(p.coverageId(),date,date,null,zone.getId(),"VND",Map.of("CLINICAL","x"),
                p.sourceObservedAt(),p.reconciledAt(),p.acceptedAt(),p.acceptedBy(),"accepted","a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->proof(date,LocalDate.of(2026,11,1),null)).isInstanceOf(IllegalArgumentException.class);
    }
    private OutpatientCoverage proof(LocalDate from,LocalDate to,UUID department){
        var refs=new HashMap<String,String>();OutpatientCoverage.REQUIRED_SOURCES.forEach(s->refs.put(s,"isolated-test-export:"+s));
        Instant at=Instant.parse("2026-10-13T01:00:00Z");
        return new OutpatientCoverage(UUID.randomUUID(),from,to,department,zone.getId(),"VND",refs,at,at,at,
                UUID.randomUUID(),"ISOLATED_TEST_ONLY","a".repeat(64));
    }
    private OutpatientReportReadPort.Day day(long visits,String cash,String refund){
        return new OutpatientReportReadPort.Day(date,visits,2,1,3,new BigDecimal(cash),new BigDecimal(refund));
    }
    private void stub(OutpatientCoverage proof,String hash,boolean pending,List<OutpatientReportReadPort.Day> days){
        when(store.load(date,date,null,zone.getId(),"VND")).thenReturn(Optional.of(new OutpatientReportReadPort.Evidence(proof,hash,pending,days)));
    }
    private void assertUnavailable(){assertThatThrownBy(()->service.daily(date,date,null)).isInstanceOf(ReportProjectionUnavailableException.class);}
}
