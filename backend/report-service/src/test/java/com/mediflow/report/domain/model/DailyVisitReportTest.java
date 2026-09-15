package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.exception.ReportRuleException;

class DailyVisitReportTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 14);

    @Test
    void initialize_validScope_startsWithZeroCounters() {
        UUID departmentId = UUID.randomUUID();

        DailyVisitReport report = DailyVisitReport.initialize(DATE, departmentId);

        assertThat(report.getReportDate()).isEqualTo(DATE);
        assertThat(report.getDepartmentId()).isEqualTo(departmentId);
        assertThat(report.getVisitCount()).isZero();
        assertThat(report.getLabCount()).isZero();
        assertThat(report.getPrescriptionCount()).isZero();
        assertThat(report.getRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void incrementVisits_negativeDelta_throwsReportRule() {
        DailyVisitReport report = DailyVisitReport.initialize(DATE, null);

        assertThatThrownBy(() -> report.incrementVisits(-1))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_VISIT_DELTA_INVALID"));
    }

    @Test
    void incrementCounters_positiveDelta_updatesEachCounter() {
        DailyVisitReport report = DailyVisitReport.initialize(DATE, null);

        report.incrementVisits(2);
        report.incrementLabs(3);
        report.incrementPrescriptions(4);

        assertThat(report.getVisitCount()).isEqualTo(2);
        assertThat(report.getLabCount()).isEqualTo(3);
        assertThat(report.getPrescriptionCount()).isEqualTo(4);
    }

    @Test
    void addRevenue_signedDeltas_updatesWithScaleTwo() {
        DailyVisitReport report = DailyVisitReport.initialize(DATE, null);

        report.addRevenue(new BigDecimal("100.50"));
        report.addRevenue(new BigDecimal("-0.50"));

        assertThat(report.getRevenue()).isEqualByComparingTo("100.00");
        assertThat(report.getRevenue().scale()).isEqualTo(2);
    }

    @Test
    void addRevenue_belowZero_throwsReportRule() {
        DailyVisitReport report = DailyVisitReport.initialize(DATE, null);

        assertThatThrownBy(() -> report.addRevenue(new BigDecimal("-0.01")))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_REVENUE_NEGATIVE"));
    }

    @Test
    void addRevenue_moreThanTwoDecimals_throwsReportRule() {
        DailyVisitReport report = DailyVisitReport.initialize(DATE, null);

        assertThatThrownBy(() -> report.addRevenue(new BigDecimal("1.001")))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_AMOUNT_SCALE_INVALID"));
    }
}
