package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.port.out.OutpatientStatisticsReadPort;
import com.mediflow.report.application.port.out.OutpatientStatisticsReadPort.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutpatientStatisticsReadServiceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 3);
    private static final UUID INTERNAL = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PEDIATRIC = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private final OutpatientStatisticsReadPort store = mock(OutpatientStatisticsReadPort.class);
    private final OutpatientStatisticsReadService service =
            new OutpatientStatisticsReadService(store, ZoneId.of("Asia/Bangkok"));

    @Test
    void splitsRevenueByServiceLineAndDepartmentAndFillsEveryDay() {
        when(store.load(FROM, TO, null, "Asia/Bangkok", "VND")).thenReturn(List.of(
                new Row(FROM, INTERNAL, "VISITS", new BigDecimal("2.000")),
                new Row(FROM, INTERNAL, "EXAM", new BigDecimal("100000.00")),
                new Row(FROM, INTERNAL, "PRESCRIPTION", new BigDecimal("30000.00")),
                new Row(TO, PEDIATRIC, "VISITS", new BigDecimal("1.000")),
                new Row(TO, PEDIATRIC, "LAB_TEST", new BigDecimal("36000.00")),
                new Row(TO, PEDIATRIC, "UNCLASSIFIED", new BigDecimal("5000.00")),
                new Row(TO, PEDIATRIC, "REFUND", new BigDecimal("10000.00"))));

        var result = service.statistics(FROM, TO, null);

        assertThat(result.reconciled()).isFalse();
        assertThat(result.days()).extracting(d -> d.date()).containsExactly(FROM, FROM.plusDays(1), TO);
        assertThat(result.days().get(1).completedVisits()).isZero();
        assertThat(result.days().get(1).examRevenue()).isEqualByComparingTo("0");
        assertThat(result.totals().completedVisits()).isEqualTo(3);
        assertThat(result.totals().examRevenue()).isEqualByComparingTo("100000");
        assertThat(result.totals().prescriptionRevenue()).isEqualByComparingTo("30000");
        assertThat(result.totals().labRevenue()).isEqualByComparingTo("36000");
        assertThat(result.totals().unclassifiedRevenue()).isEqualByComparingTo("5000");
        assertThat(result.totals().refunds()).isEqualByComparingTo("10000");
        assertThat(result.departments()).extracting(d -> d.departmentId()).containsExactly(INTERNAL, PEDIATRIC);
        assertThat(result.departments().get(0).completedVisits()).isEqualTo(2);
        assertThat(result.departments().get(1).labRevenue()).isEqualByComparingTo("36000");
    }

    @Test
    void rejectsInvertedOrOverlongRangeBeforeReading() {
        assertThatThrownBy(() -> service.statistics(TO, FROM, null)).isInstanceOf(ReportPeriodValidationException.class);
        assertThatThrownBy(() -> service.statistics(FROM, FROM.plusDays(366), null))
                .isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsRowsOutsideTheRequestedScope() {
        when(store.load(any(), any(), any(), any(), any())).thenReturn(List.of(
                new Row(FROM, PEDIATRIC, "EXAM", new BigDecimal("1.00"))));
        assertThatThrownBy(() -> service.statistics(FROM, TO, INTERNAL)).isInstanceOf(IllegalStateException.class);
    }
}
