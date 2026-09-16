package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;
import com.mediflow.report.application.exception.ReportDateRangeException;
import com.mediflow.report.application.mapper.ReportDtoMapper;
import com.mediflow.report.application.port.out.DailyVisitReportRepositoryPort;
import com.mediflow.report.application.port.out.DrugStatisticRepositoryPort;
import com.mediflow.report.application.port.out.MonthlyRevenueReportRepositoryPort;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.MonthlyRevenueReport;
import com.mediflow.report.domain.model.TopMedicineSummary;

@ExtendWith(MockitoExtension.class)
class ReportApplicationServiceTest {

    private static final UUID DEPARTMENT_ID = UUID.randomUUID();

    @Mock private DailyVisitReportRepositoryPort dailyReports;
    @Mock private MonthlyRevenueReportRepositoryPort monthlyReports;
    @Mock private DrugStatisticRepositoryPort drugStatistics;

    private ReportApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReportApplicationService(dailyReports, monthlyReports, drugStatistics,
                Mappers.getMapper(ReportDtoMapper.class));
    }

    @Test
    void daily_noData_returnsZeroForRequestedScope() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(dailyReports.find(date, null)).thenReturn(Optional.empty());

        DailyReportDTO result = service.daily(date, null);

        assertThat(result.reportDate()).isEqualTo(date);
        assertThat(result.departmentId()).isNull();
        assertThat(result.visitCount()).isZero();
        assertThat(result.labCount()).isZero();
        assertThat(result.prescriptionCount()).isZero();
        assertThat(result.revenue()).isEqualByComparingTo(new BigDecimal("0.00"));
        verify(dailyReports).find(date, null);
        verify(dailyReports, never()).findRange(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void monthly_noData_leapYearReturnsEveryCalendarDay() {
        when(monthlyReports.find(2028, 2, DEPARTMENT_ID)).thenReturn(Optional.empty());
        when(dailyReports.findRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29),
                DEPARTMENT_ID)).thenReturn(List.of());

        MonthlyReportDTO result = service.monthly(2, 2028, DEPARTMENT_ID);

        assertThat(result.dailyDetails()).hasSize(29);
        assertThat(result.dailyDetails()).extracting(DailyReportDTO::reportDate)
                .startsWith(LocalDate.of(2028, 2, 1))
                .endsWith(LocalDate.of(2028, 2, 29));
        assertThat(result.dailyDetails()).allMatch(row -> row.visitCount() == 0
                && row.labCount() == 0 && row.prescriptionCount() == 0
                && row.revenue().scale() == 2);
        verify(dailyReports).findRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29),
                DEPARTMENT_ID);
    }

    @Test
    void monthly_partialData_zeroFillsMissingDaysWithOneRangeQuery() {
        MonthlyRevenueReport aggregate = MonthlyRevenueReport.initialize(9, 2026, null);
        aggregate.addRevenue(new BigDecimal("12.30"));
        aggregate.adjustInvoiceCount(1);
        DailyVisitReport existing = DailyVisitReport.initialize(LocalDate.of(2026, 9, 3), null);
        existing.incrementVisits(2);
        when(monthlyReports.find(2026, 9, null)).thenReturn(Optional.of(aggregate));
        when(dailyReports.findRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null))
                .thenReturn(List.of(existing));

        MonthlyReportDTO result = service.monthly(9, 2026, null);

        assertThat(result.totalRevenue()).isEqualByComparingTo("12.30");
        assertThat(result.invoiceCount()).isEqualTo(1);
        assertThat(result.dailyDetails()).hasSize(30);
        assertThat(result.dailyDetails().get(2).visitCount()).isEqualTo(2);
        assertThat(result.dailyDetails().get(1).visitCount()).isZero();
        verify(dailyReports).findRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        verify(dailyReports, never()).find(eq(LocalDate.of(2026, 9, 1)), eq(null));
    }

    @Test
    void topMedicine_validatesRangeAndAcceptsMaximumLimit() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        UUID drugId = UUID.randomUUID();
        when(drugStatistics.topMedicines(from, to, null, 50))
                .thenReturn(List.of(new TopMedicineSummary(drugId, "A", 4)));

        List<TopMedicineDTO> result = service.topMedicines(from, to, null, 50);

        assertThat(result).extracting(TopMedicineDTO::drugId).containsExactly(drugId);
        verify(drugStatistics).topMedicines(from, to, null, 50);
    }

    @Test
    void topMedicine_zeroLimitIsRejected() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        assertThatThrownBy(() -> service.topMedicines(from, to, DEPARTMENT_ID, 0))
                .isInstanceOfSatisfying(com.mediflow.report.domain.exception.ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_LIMIT_INVALID"));

        verifyNoInteractions(drugStatistics);
    }

    @Test
    void topMedicine_limitAboveMaximumIsRejected() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        assertThatThrownBy(() -> service.topMedicines(from, to, DEPARTMENT_ID, 51))
                .isInstanceOfSatisfying(com.mediflow.report.domain.exception.ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_LIMIT_INVALID"));

        verifyNoInteractions(drugStatistics);
    }

    @Test
    void topMedicine_invalidRangeThrowsTypedExceptionBeforeRepository() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        assertThatThrownBy(() -> service.topMedicines(from, to, null, 10))
                .isInstanceOfSatisfying(ReportDateRangeException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DATE_RANGE_INVALID"));

        verifyNoInteractions(drugStatistics);
    }

    @Test
    void topMedicine_missingDateIsValidationErrorBeforeRepository() {
        assertThatThrownBy(() -> service.topMedicines(null, LocalDate.of(2026, 9, 30), null, 10))
                .isInstanceOfSatisfying(com.mediflow.report.domain.exception.ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DATE_REQUIRED"));

        verifyNoInteractions(drugStatistics);
    }

    @Test
    void monthly_invalidMonthIsRejectedBeforeRepository() {
        assertThatThrownBy(() -> service.monthly(13, 2026, null))
                .isInstanceOfSatisfying(com.mediflow.report.domain.exception.ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_MONTH_INVALID"));

        verifyNoInteractions(monthlyReports, dailyReports);
    }

    @Test
    void monthly_invalidYearIsRejectedBeforeRepository() {
        assertThatThrownBy(() -> service.monthly(9, 1999, null))
                .isInstanceOfSatisfying(com.mediflow.report.domain.exception.ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_YEAR_INVALID"));

        verifyNoInteractions(monthlyReports, dailyReports);
    }
}
