package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.DrugStatistic;
import com.mediflow.report.domain.model.MonthlyRevenueReport;
import com.mediflow.report.domain.model.PaymentContribution;
import com.mediflow.report.domain.model.PaymentContributionStatus;

class PersistenceMappingTest {

    @Test
    void dailyRoundTrip_preservesProjectionFields() {
        DailyVisitReport source = DailyVisitReport.initialize(LocalDate.of(2026, 9, 1), null);
        source.incrementVisits(2);
        source.incrementLabs(1);
        source.addRevenue(new BigDecimal("10.00"));

        DailyVisitReportJpaEntity entity = DailyVisitReportPersistenceMapper.toEntity(source);
        DailyVisitReport restored = DailyVisitReportPersistenceMapper.toDomain(entity);

        assertThat(restored.getReportDate()).isEqualTo(source.getReportDate());
        assertThat(restored.getVisitCount()).isEqualTo(2);
        assertThat(restored.getLabCount()).isEqualTo(1);
        assertThat(restored.getRevenue()).isEqualByComparingTo("10.00");
    }

    @Test
    void monthlyAndDrugRoundTrip_preserveNaturalKeys() {
        UUID departmentId = UUID.randomUUID();
        MonthlyRevenueReport monthly = MonthlyRevenueReport.initialize(9, 2026, departmentId);
        monthly.addRevenue(new BigDecimal("42.50"));
        DrugStatistic drug = DrugStatistic.initialize(UUID.randomUUID(), "  Paracetamol  ",
                LocalDate.of(2026, 9, 1), departmentId);
        drug.incrementQuantity(3);

        MonthlyRevenueReport restoredMonthly = MonthlyRevenueReportPersistenceMapper.toDomain(
                MonthlyRevenueReportPersistenceMapper.toEntity(monthly));
        DrugStatistic restoredDrug = DrugStatisticPersistenceMapper.toDomain(
                DrugStatisticPersistenceMapper.toEntity(drug));

        assertThat(restoredMonthly.getDepartmentId()).isEqualTo(departmentId);
        assertThat(restoredMonthly.getTotalRevenue()).isEqualByComparingTo("42.50");
        assertThat(restoredDrug.getDrugName()).isEqualTo("Paracetamol");
        assertThat(restoredDrug.getDispensedQuantity()).isEqualTo(3);
    }

    @Test
    void paymentContributionRoundTrip_preservesLifecycleState() {
        UUID invoiceId = UUID.randomUUID();
        PaymentContribution source = PaymentContribution.initialize(invoiceId);
        source.complete(UUID.randomUUID(), LocalDate.of(2026, 9, 1), null, new BigDecimal("8.00"));
        source.fail(UUID.randomUUID());

        PaymentContribution restored = PaymentContributionPersistenceMapper.toDomain(
                PaymentContributionPersistenceMapper.toEntity(source));

        assertThat(restored.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(restored.getStatus()).isEqualTo(PaymentContributionStatus.REVERSED);
        assertThat(restored.getAmount()).isEqualByComparingTo("8.00");
    }
}
