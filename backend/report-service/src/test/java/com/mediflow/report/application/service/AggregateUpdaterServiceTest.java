package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.report.application.dto.command.DispensedItem;
import com.mediflow.report.application.port.out.DailyVisitReportRepositoryPort;
import com.mediflow.report.application.port.out.DrugStatisticRepositoryPort;
import com.mediflow.report.application.port.out.MonthlyRevenueReportRepositoryPort;
import com.mediflow.report.application.port.out.PaymentContributionRepositoryPort;
import com.mediflow.report.application.port.out.ProcessedEventPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.DrugStatistic;
import com.mediflow.report.domain.model.MonthlyRevenueReport;
import com.mediflow.report.domain.model.PaymentContribution;
import com.mediflow.report.domain.model.PaymentContributionStatus;

@ExtendWith(MockitoExtension.class)
class AggregateUpdaterServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID PRESCRIPTION_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID DRUG_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DRUG_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    @Mock private ProcessedEventPort processedEvents;
    @Mock private DailyVisitReportRepositoryPort dailyReports;
    @Mock private DrugStatisticRepositoryPort drugStatistics;
    @Mock private MonthlyRevenueReportRepositoryPort monthlyReports;
    @Mock private PaymentContributionRepositoryPort paymentContributions;

    private AggregateUpdaterService service;

    @BeforeEach
    void setUp() {
        service = new AggregateUpdaterService(processedEvents, dailyReports, drugStatistics,
                monthlyReports, paymentContributions);
    }

    @Test
    void medicalRecord_updatesHospitalAndDepartmentExactlyOnce() {
        DailyVisitReport hospital = DailyVisitReport.initialize(DATE, null);
        DailyVisitReport department = DailyVisitReport.initialize(DATE, DEPARTMENT_ID);
        when(processedEvents.claimIfAbsent(EVENT_ID, "medicalrecord.created")).thenReturn(true);
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(hospital);
        when(dailyReports.findOrCreate(DATE, DEPARTMENT_ID)).thenReturn(department);

        service.onMedicalRecordCreated(EVENT_ID, DATE, DEPARTMENT_ID);

        assertThat(hospital.getVisitCount()).isEqualTo(1);
        assertThat(department.getVisitCount()).isEqualTo(1);
        verify(dailyReports).save(hospital);
        verify(dailyReports).save(department);
    }

    @Test
    void redeliveredLabEvent_doesNotTouchProjection() {
        when(processedEvents.claimIfAbsent(EVENT_ID, "lab.result.created")).thenReturn(false);

        service.onLabResultCreated(EVENT_ID, DATE, DEPARTMENT_ID);

        verify(dailyReports, never()).findOrCreate(any(), any());
        verify(dailyReports, never()).save(any());
    }

    @Test
    void invalidOperationalPayload_isRejectedBeforeClaim() {
        assertThatThrownBy(() -> service.onMedicalRecordCreated(EVENT_ID, DATE, null))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DEPARTMENT_ID_REQUIRED"));

        verify(processedEvents, never()).claimIfAbsent(any(), any());
    }

    @Test
    void prescription_groupsDuplicateItemsAndLocksDrugsInSortedOrder() {
        DailyVisitReport hospital = DailyVisitReport.initialize(DATE, null);
        DailyVisitReport department = DailyVisitReport.initialize(DATE, DEPARTMENT_ID);
        DrugStatistic hospitalA = DrugStatistic.initialize(DRUG_A, "A", DATE, null);
        DrugStatistic departmentA = DrugStatistic.initialize(DRUG_A, "A", DATE, DEPARTMENT_ID);
        DrugStatistic hospitalB = DrugStatistic.initialize(DRUG_B, "B", DATE, null);
        DrugStatistic departmentB = DrugStatistic.initialize(DRUG_B, "B", DATE, DEPARTMENT_ID);
        when(processedEvents.claimIfAbsent(EVENT_ID, "prescription.filled")).thenReturn(true);
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(hospital);
        when(dailyReports.findOrCreate(DATE, DEPARTMENT_ID)).thenReturn(department);
        when(drugStatistics.findOrCreate(DRUG_A, "A-new", DATE, null)).thenReturn(hospitalA);
        when(drugStatistics.findOrCreate(DRUG_A, "A-new", DATE, DEPARTMENT_ID)).thenReturn(departmentA);
        when(drugStatistics.findOrCreate(DRUG_B, "B", DATE, null)).thenReturn(hospitalB);
        when(drugStatistics.findOrCreate(DRUG_B, "B", DATE, DEPARTMENT_ID)).thenReturn(departmentB);

        service.onPrescriptionFilled(EVENT_ID, Instant.parse("2026-09-14T17:00:00Z"), DEPARTMENT_ID,
                PRESCRIPTION_ID, List.of(new DispensedItem(DRUG_B, "B", 2),
                        new DispensedItem(DRUG_A, "A", 1), new DispensedItem(DRUG_A, "A-new", 3)));

        assertThat(hospital.getPrescriptionCount()).isEqualTo(1);
        assertThat(department.getPrescriptionCount()).isEqualTo(1);
        assertThat(hospitalA.getDispensedQuantity()).isEqualTo(4);
        assertThat(departmentA.getDispensedQuantity()).isEqualTo(4);
        assertThat(hospitalB.getDispensedQuantity()).isEqualTo(2);
        InOrder order = inOrder(drugStatistics);
        order.verify(drugStatistics).findOrCreate(DRUG_A, "A-new", DATE, null);
        order.verify(drugStatistics).findOrCreate(DRUG_A, "A-new", DATE, DEPARTMENT_ID);
        order.verify(drugStatistics).findOrCreate(DRUG_B, "B", DATE, null);
        order.verify(drugStatistics).findOrCreate(DRUG_B, "B", DATE, DEPARTMENT_ID);
    }

    @Test
    void prescription_invalidItems_areRejectedBeforeClaim() {
        assertThatThrownBy(() -> service.onPrescriptionFilled(EVENT_ID, Instant.now(), DEPARTMENT_ID,
                PRESCRIPTION_ID, List.of()))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DRUG_ITEMS_REQUIRED"));

        verify(processedEvents, never()).claimIfAbsent(any(), eq("prescription.filled"));
    }

    @Test
    void prescription_itemWithTooLongName_isRejectedBeforeClaim() {
        assertThatThrownBy(() -> new DispensedItem(DRUG_A, "x".repeat(151), 1))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DRUG_NAME_TOO_LONG"));
        verify(processedEvents, never()).claimIfAbsent(any(), eq("prescription.filled"));
    }

    @Test
    void paymentCompleted_updatesDailyAndMonthlyHospitalThenDepartment() {
        DailyVisitReport hospitalDaily = DailyVisitReport.initialize(DATE, null);
        DailyVisitReport departmentDaily = DailyVisitReport.initialize(DATE, DEPARTMENT_ID);
        MonthlyRevenueReport hospitalMonthly = MonthlyRevenueReport.initialize(9, 2026, null);
        MonthlyRevenueReport departmentMonthly = MonthlyRevenueReport.initialize(9, 2026, DEPARTMENT_ID);
        when(processedEvents.claimIfAbsent(EVENT_ID, "payment.completed")).thenReturn(true);
        when(paymentContributions.findOrCreateForUpdate(INVOICE_ID))
                .thenReturn(PaymentContribution.initialize(INVOICE_ID));
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(hospitalDaily);
        when(dailyReports.findOrCreate(DATE, DEPARTMENT_ID)).thenReturn(departmentDaily);
        when(monthlyReports.findOrCreate(2026, 9, null)).thenReturn(hospitalMonthly);
        when(monthlyReports.findOrCreate(2026, 9, DEPARTMENT_ID)).thenReturn(departmentMonthly);

        service.onPaymentCompleted(EVENT_ID, Instant.parse("2026-09-14T17:00:00Z"), INVOICE_ID,
                DEPARTMENT_ID, new BigDecimal("100.00"));

        assertThat(hospitalDaily.getRevenue()).isEqualByComparingTo("100.00");
        assertThat(departmentDaily.getRevenue()).isEqualByComparingTo("100.00");
        assertThat(hospitalMonthly.getTotalRevenue()).isEqualByComparingTo("100.00");
        assertThat(hospitalMonthly.getInvoiceCount()).isEqualTo(1);
        assertThat(departmentMonthly.getInvoiceCount()).isEqualTo(1);
    }

    @Test
    void paymentFailedAfterCompleted_reversesOriginalContribution() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        contribution.complete(UUID.randomUUID(), DATE, DEPARTMENT_ID, new BigDecimal("100.00"));
        DailyVisitReport daily = DailyVisitReport.initialize(DATE, null);
        daily.addRevenue(new BigDecimal("100.00"));
        DailyVisitReport departmentDaily = DailyVisitReport.initialize(DATE, DEPARTMENT_ID);
        departmentDaily.addRevenue(new BigDecimal("100.00"));
        MonthlyRevenueReport monthly = MonthlyRevenueReport.initialize(9, 2026, null);
        monthly.addRevenue(new BigDecimal("100.00"));
        monthly.adjustInvoiceCount(1);
        MonthlyRevenueReport departmentMonthly = MonthlyRevenueReport.initialize(9, 2026, DEPARTMENT_ID);
        departmentMonthly.addRevenue(new BigDecimal("100.00"));
        departmentMonthly.adjustInvoiceCount(1);
        when(processedEvents.claimIfAbsent(EVENT_ID, "payment.failed")).thenReturn(true);
        when(paymentContributions.findOrCreateForUpdate(INVOICE_ID)).thenReturn(contribution);
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(daily);
        when(dailyReports.findOrCreate(DATE, DEPARTMENT_ID)).thenReturn(departmentDaily);
        when(monthlyReports.findOrCreate(2026, 9, null)).thenReturn(monthly);
        when(monthlyReports.findOrCreate(2026, 9, DEPARTMENT_ID)).thenReturn(departmentMonthly);

        service.onPaymentFailed(EVENT_ID, Instant.parse("2026-09-16T01:00:00Z"), INVOICE_ID);

        assertThat(daily.getRevenue()).isZero();
        assertThat(departmentDaily.getRevenue()).isZero();
        assertThat(monthly.getTotalRevenue()).isZero();
        assertThat(monthly.getInvoiceCount()).isZero();
        assertThat(departmentMonthly.getTotalRevenue()).isZero();
    }

    @Test
    void paymentFailedBeforeCompleted_persistsPendingWithoutProjectionEffect() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        when(processedEvents.claimIfAbsent(EVENT_ID, "payment.failed")).thenReturn(true);
        when(paymentContributions.findOrCreateForUpdate(INVOICE_ID)).thenReturn(contribution);

        service.onPaymentFailed(EVENT_ID, Instant.now(), INVOICE_ID);

        assertThat(contribution.getStatus()).isEqualTo(com.mediflow.report.domain.model.PaymentContributionStatus.PENDING_REVERSAL);
        verify(paymentContributions).save(contribution);
        verifyNoInteractions(dailyReports, monthlyReports);
    }

    @Test
    void paymentCompleted_sameEventRedelivery_appliesOnlyOnce() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        DailyVisitReport daily = DailyVisitReport.initialize(DATE, null);
        MonthlyRevenueReport monthly = MonthlyRevenueReport.initialize(9, 2026, null);
        when(processedEvents.claimIfAbsent(EVENT_ID, "payment.completed")).thenReturn(true, false);
        when(paymentContributions.findOrCreateForUpdate(INVOICE_ID)).thenReturn(contribution);
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(daily);
        when(monthlyReports.findOrCreate(2026, 9, null)).thenReturn(monthly);

        service.onPaymentCompleted(EVENT_ID, Instant.parse("2026-09-15T01:00:00Z"), INVOICE_ID,
                null, new BigDecimal("100.00"));
        service.onPaymentCompleted(EVENT_ID, Instant.parse("2026-09-15T01:00:00Z"), INVOICE_ID,
                null, new BigDecimal("100.00"));

        assertThat(daily.getRevenue()).isEqualByComparingTo("100.00");
        assertThat(monthly.getTotalRevenue()).isEqualByComparingTo("100.00");
        assertThat(monthly.getInvoiceCount()).isEqualTo(1);
        verify(paymentContributions, times(1)).findOrCreateForUpdate(INVOICE_ID);
        verify(dailyReports, times(1)).findOrCreate(DATE, null);
        verify(monthlyReports, times(1)).findOrCreate(2026, 9, null);
    }

    @Test
    void paymentCompleted_differentEventsSameInvoice_appliesOnlyOnce() {
        UUID secondEventId = UUID.randomUUID();
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        DailyVisitReport daily = DailyVisitReport.initialize(DATE, null);
        MonthlyRevenueReport monthly = MonthlyRevenueReport.initialize(9, 2026, null);
        when(processedEvents.claimIfAbsent(any(), eq("payment.completed"))).thenReturn(true);
        when(paymentContributions.findOrCreateForUpdate(INVOICE_ID)).thenReturn(contribution);
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(daily);
        when(monthlyReports.findOrCreate(2026, 9, null)).thenReturn(monthly);

        service.onPaymentCompleted(EVENT_ID, Instant.parse("2026-09-15T01:00:00Z"), INVOICE_ID,
                null, new BigDecimal("100.00"));
        service.onPaymentCompleted(secondEventId, Instant.parse("2026-09-15T01:00:00Z"), INVOICE_ID,
                null, new BigDecimal("100.00"));

        assertThat(contribution.getStatus()).isEqualTo(PaymentContributionStatus.APPLIED);
        assertThat(daily.getRevenue()).isEqualByComparingTo("100.00");
        assertThat(monthly.getTotalRevenue()).isEqualByComparingTo("100.00");
        assertThat(monthly.getInvoiceCount()).isEqualTo(1);
        verify(paymentContributions, times(2)).findOrCreateForUpdate(INVOICE_ID);
        verify(dailyReports, times(1)).findOrCreate(DATE, null);
    }

    @Test
    void paymentCompleted_nullDepartment_updatesHospitalOnly() {
        PaymentContribution contribution = PaymentContribution.initialize(INVOICE_ID);
        DailyVisitReport hospitalDaily = DailyVisitReport.initialize(DATE, null);
        MonthlyRevenueReport hospitalMonthly = MonthlyRevenueReport.initialize(9, 2026, null);
        when(processedEvents.claimIfAbsent(EVENT_ID, "payment.completed")).thenReturn(true);
        when(paymentContributions.findOrCreateForUpdate(INVOICE_ID)).thenReturn(contribution);
        when(dailyReports.findOrCreate(DATE, null)).thenReturn(hospitalDaily);
        when(monthlyReports.findOrCreate(2026, 9, null)).thenReturn(hospitalMonthly);

        service.onPaymentCompleted(EVENT_ID, Instant.parse("2026-09-15T01:00:00Z"), INVOICE_ID,
                null, new BigDecimal("75.50"));

        assertThat(hospitalDaily.getRevenue()).isEqualByComparingTo("75.50");
        assertThat(hospitalMonthly.getTotalRevenue()).isEqualByComparingTo("75.50");
        verify(dailyReports, never()).findOrCreate(eq(DATE), eq(DEPARTMENT_ID));
        verify(monthlyReports, never()).findOrCreate(eq(2026), eq(9), eq(DEPARTMENT_ID));
    }
}
