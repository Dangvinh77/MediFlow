package com.mediflow.report.application.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort.Day;
import com.mediflow.report.domain.model.FinancialAmounts;
import com.mediflow.report.domain.model.FinancialReportPublication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialSnapshotReadServiceTest {
    private final FinancialSnapshotReadPort store=mock(FinancialSnapshotReadPort.class);
    private final FinancialSnapshotReadService service=new FinancialSnapshotReadService(store,ZoneId.of("Asia/Bangkok"),"VND",
            Clock.fixed(Instant.parse("2027-01-02T00:00:00Z"),ZoneOffset.UTC));
    private final UUID generation=UUID.randomUUID();
    private final LocalDate from=LocalDate.of(2026,1,1),to=LocalDate.of(2026,12,31);
    private final Instant observed=Instant.parse("2027-01-01T00:00:00Z");
    private FinancialReportPublication publication(String currency,String zone) {
        return new FinancialReportPublication(generation,from,to,currency,zone,observed,observed.plusSeconds(1),observed.plusSeconds(2),"accepted-test-manifest");
    }
    private FinancialAmounts amounts(String cash,String liability,String earned,String refunds,String debt) {
        return new FinancialAmounts(new BigDecimal(cash),new BigDecimal(liability),new BigDecimal(earned),new BigDecimal(refunds),new BigDecimal(debt));
    }
    private List<Day> year(int year) {
        var values=new ArrayList<Day>();
        for(LocalDate day=LocalDate.of(year,1,1);day.getYear()==year;day=day.plusDays(1))
            values.add(new Day(day,amounts("1","100","2","0.5","40")));
        return values;
    }
    @BeforeEach void ready() { when(store.publication("VND","Asia/Bangkok")).thenReturn(Optional.of(publication("VND","Asia/Bangkok"))); }
    @Test void daily_acceptedScope_returnsSortedCompleteMetricsAndAsOfEvidence() {
        UUID department=UUID.randomUUID();
        when(store.read(generation,from,from.plusDays(1),department)).thenReturn(List.of(
                new Day(from.plusDays(1),amounts("40","0","100","15","15")),new Day(from,amounts("60","60","0","0","40"))));
        var result=service.daily(from,from.plusDays(1),department);
        assertThat(result.departmentId()).isEqualTo(department); assertThat(result.generationId()).isEqualTo(generation);
        assertThat(result.sourceObservedAt()).isEqualTo(observed); assertThat(result.snapshotOnly()).isTrue();
        assertThat(result.periods()).extracting(period -> period.from()).containsExactly(from,from.plusDays(1));
        assertThat(result.periods().getFirst().earnedRevenue()).isZero();
    }
    @Test void monthly_fullYear_sumsOnlyFlowsAndReturns12ExplicitClosingStocks() {
        when(store.read(generation,from,to,null)).thenReturn(year(2026));
        var result=service.monthly(2026,null); assertThat(result.periods()).hasSize(12);
        var january=result.periods().getFirst();
        assertThat(january.cashReceived()).isEqualByComparingTo("31"); assertThat(january.earnedRevenue()).isEqualByComparingTo("62");
        assertThat(january.refunds()).isEqualByComparingTo("15.5"); assertThat(january.depositLiability()).isEqualByComparingTo("100");
        assertThat(january.outstandingReceivable()).isEqualByComparingTo("40");
        assertThat(january.to()).isEqualTo(from.withDayOfMonth(31));
    }
    @Test void monthly_leapYear_includesFeb29WithoutWrongPeriodBound() {
        LocalDate start=LocalDate.of(2024,1,1),end=LocalDate.of(2024,12,31); Instant seen=Instant.parse("2025-01-01T00:00:00Z");
        when(store.publication("VND","Asia/Bangkok")).thenReturn(Optional.of(new FinancialReportPublication(generation,start,end,"VND","Asia/Bangkok",seen,seen,seen,"leap-test")));
        when(store.read(generation,start,end,null)).thenReturn(year(2024));
        var february=service.monthly(2024,null).periods().get(1);
        assertThat(february.cashReceived()).isEqualByComparingTo("29"); assertThat(february.to()).isEqualTo(LocalDate.of(2024,2,29));
    }
    @Test void daily_noPublication_doesNotReadCashOnlyTablesOrReturnZero() {
        when(store.publication("VND","Asia/Bangkok")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.daily(from,from,null)).isInstanceOf(ReportProjectionUnavailableException.class);
        verify(store,never()).read(any(),any(),any(),any());
    }
    @Test void daily_futureAsOfEvidence_isUnavailableNotInventedFreshness() {
        var historicalClock=new FinancialSnapshotReadService(store,ZoneId.of("Asia/Bangkok"),"VND",
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"),ZoneOffset.UTC));
        assertThatThrownBy(() -> historicalClock.daily(from,from,null)).isInstanceOf(ReportProjectionUnavailableException.class);
        verify(store,never()).read(any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"currency","zone","period"})
    void daily_wrongAcceptedDimension_isUnavailable(String dimension) {
        when(store.publication("VND","Asia/Bangkok")).thenReturn(Optional.of(publication(dimension.equals("currency")?"USD":"VND",dimension.equals("zone")?"UTC":"Asia/Bangkok")));
        LocalDate date=dimension.equals("period")?to.plusDays(1):from;
        assertThatThrownBy(() -> service.daily(date,date,null)).isInstanceOf(ReportProjectionUnavailableException.class);
        verify(store,never()).read(any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate","outside","null-date","null-amount","null-row"})
    void daily_incompleteOrCorruptRows_returnsUnavailableNotInventedStock(String mode) {
        var good=new Day(from,amounts("0","0","0","0","0"));
        var rows=new ArrayList<Day>();
        switch(mode) {
            case "missing" -> { }
            case "duplicate" -> { rows.add(good); rows.add(good); }
            case "outside" -> rows.add(new Day(from.minusDays(1),good.amounts()));
            case "null-date" -> rows.add(new Day(null,good.amounts()));
            case "null-amount" -> rows.add(new Day(from,null));
            default -> rows.add(null);
        }
        when(store.read(generation,from,from,null)).thenReturn(rows);
        assertThatThrownBy(() -> service.daily(from,from,null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @Test void daily_coveredExplicitZeroDay_isAllowedButUnknownDepartmentStillUnavailable() {
        when(store.read(generation,from,from,null)).thenReturn(List.of(new Day(from,amounts("0","0","0","0","0"))));
        assertThat(service.daily(from,from,null).periods().getFirst().earnedRevenue()).isZero();
        UUID unknown=UUID.randomUUID(); when(store.read(generation,from,from,unknown)).thenReturn(List.of());
        assertThatThrownBy(() -> service.daily(from,from,unknown)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(ints={1999,2101,-1,Integer.MAX_VALUE})
    void monthly_invalidYear_failsBeforePort(int year) {
        clearInvocations(store);
        assertThatThrownBy(() -> service.monthly(year,null)).isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store);
    }
    @Test void daily_invalidPeriods_failBeforePort() {
        for(LocalDate[] period:new LocalDate[][]{{null,from},{from,null},{to,from},{from,from.plusDays(366)},
                {LocalDate.of(1999,12,31),from},{to,LocalDate.of(2101,1,1)}}) {
            assertThatThrownBy(() -> service.daily(period[0],period[1],null)).isInstanceOf(ReportPeriodValidationException.class);
        }
        verifyNoInteractions(store);
    }
    @Test void monthly_numericCapacityExceeded_isUnavailableNotRounded() {
        var rows=year(2026); rows.set(0,new Day(from,amounts("99999999999999999.99","0","0","0","0")));
        when(store.read(generation,from,to,null)).thenReturn(rows);
        assertThatThrownBy(() -> service.monthly(2026,null)).isInstanceOf(ReportProjectionUnavailableException.class);
    }
}
