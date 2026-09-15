package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.exception.ReportRuleException;

class MonthlyRevenueReportTest {

    @Test
    void initialize_validMonth_startsWithZeroTotals() {
        MonthlyRevenueReport report = MonthlyRevenueReport.initialize(8, 2026, UUID.randomUUID());

        assertThat(report.getMonth()).isEqualTo(8);
        assertThat(report.getYear()).isEqualTo(2026);
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("0.00");
        assertThat(report.getInvoiceCount()).isZero();
    }

    @Test
    void initialize_monthOutsideCalendar_throwsReportRule() {
        assertThatThrownBy(() -> MonthlyRevenueReport.initialize(13, 2026, null))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_MONTH_INVALID"));
    }

    @Test
    void adjustInvoiceCount_negativeDelta_clampsAtZero() {
        MonthlyRevenueReport report = MonthlyRevenueReport.initialize(8, 2026, null);
        report.adjustInvoiceCount(2);

        report.adjustInvoiceCount(-3);

        assertThat(report.getInvoiceCount()).isZero();
    }

    @Test
    void adjustInvoiceCount_positiveDelta_updatesCount() {
        MonthlyRevenueReport report = MonthlyRevenueReport.initialize(8, 2026, null);

        report.adjustInvoiceCount(2);
        report.adjustInvoiceCount(1);

        assertThat(report.getInvoiceCount()).isEqualTo(3);
    }

    @Test
    void addRevenue_signedDeltas_updatesTotal() {
        MonthlyRevenueReport report = MonthlyRevenueReport.initialize(8, 2026, null);

        report.addRevenue(new BigDecimal("250.00"));
        report.addRevenue(new BigDecimal("-50.00"));

        assertThat(report.getTotalRevenue()).isEqualByComparingTo("200.00");
    }

    @Test
    void addRevenue_belowZero_throwsReportRule() {
        MonthlyRevenueReport report = MonthlyRevenueReport.initialize(8, 2026, null);

        assertThatThrownBy(() -> report.addRevenue(new BigDecimal("-0.01")))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_REVENUE_NEGATIVE"));
    }
}
