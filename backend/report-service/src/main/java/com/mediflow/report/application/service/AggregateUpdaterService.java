package com.mediflow.report.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.command.DispensedItem;
import com.mediflow.report.application.port.in.UpdateAggregateUseCase;
import com.mediflow.report.application.port.out.DailyVisitReportRepositoryPort;
import com.mediflow.report.application.port.out.DrugStatisticRepositoryPort;
import com.mediflow.report.application.port.out.MonthlyRevenueReportRepositoryPort;
import com.mediflow.report.application.port.out.PaymentContributionRepositoryPort;
import com.mediflow.report.application.port.out.ProcessedEventPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.DrugStatistic;
import com.mediflow.report.domain.model.PaymentContribution;
import com.mediflow.report.domain.model.PaymentContributionStatus;

import lombok.RequiredArgsConstructor;

/** Application orchestration for medical, laboratory and prescription projections. */
@Service
@RequiredArgsConstructor
public class AggregateUpdaterService implements UpdateAggregateUseCase {

    private static final String MEDICAL_RECORD_CREATED = "medicalrecord.created";
    private static final String LAB_RESULT_CREATED = "lab.result.created";
    private static final String PRESCRIPTION_FILLED = "prescription.filled";
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Bangkok");

    private final ProcessedEventPort processedEventPort;
    private final DailyVisitReportRepositoryPort dailyReports;
    private final DrugStatisticRepositoryPort drugStatistics;
    private final MonthlyRevenueReportRepositoryPort monthlyReports;
    private final PaymentContributionRepositoryPort paymentContributions;

    @Override
    @Transactional
    public void onMedicalRecordCreated(UUID eventId, LocalDate reportDate, UUID departmentId) {
        validateOperationalEvent(eventId, reportDate, departmentId);
        if (!processedEventPort.claimIfAbsent(eventId, MEDICAL_RECORD_CREATED)) {
            return;
        }
        updateDaily(reportDate, departmentId, DailyVisitReport::incrementVisits);
    }

    @Override
    @Transactional
    public void onLabResultCreated(UUID eventId, LocalDate reportDate, UUID departmentId) {
        validateOperationalEvent(eventId, reportDate, departmentId);
        if (!processedEventPort.claimIfAbsent(eventId, LAB_RESULT_CREATED)) {
            return;
        }
        updateDaily(reportDate, departmentId, DailyVisitReport::incrementLabs);
    }

    @Override
    @Transactional
    public void onPrescriptionFilled(UUID eventId, Instant occurredAt, UUID departmentId,
                                     UUID prescriptionId, List<DispensedItem> items) {
        LocalDate reportDate = validatePrescriptionEvent(eventId, occurredAt, departmentId,
                prescriptionId, items);
        List<AggregatedItem> groupedItems = groupItems(items);
        if (!processedEventPort.claimIfAbsent(eventId, PRESCRIPTION_FILLED)) {
            return;
        }

        updateDaily(reportDate, departmentId, DailyVisitReport::incrementPrescriptions);
        for (AggregatedItem item : groupedItems) {
            updateDrug(reportDate, departmentId, item);
        }
    }

    @Override
    @Transactional
    public void onPaymentCompleted(UUID eventId, Instant occurredAt, UUID invoiceId,
                                   UUID departmentId, BigDecimal amount) {
        LocalDate paymentDate = validatePaymentCompleted(eventId, occurredAt, invoiceId, amount);
        if (!processedEventPort.claimIfAbsent(eventId, "payment.completed")) {
            return;
        }
        PaymentContribution contribution = paymentContributions.findOrCreateForUpdate(invoiceId);
        PaymentContributionStatus before = contribution.getStatus();
        PaymentContribution.TransitionEffect effect = contribution.complete(eventId, paymentDate,
                departmentId, amount);
        if (before != contribution.getStatus()) {
            paymentContributions.save(contribution);
        }
        if (effect == PaymentContribution.TransitionEffect.APPLY) {
            updateRevenue(paymentDate, departmentId, amount, 1);
        }
    }

    @Override
    @Transactional
    public void onPaymentFailed(UUID eventId, Instant occurredAt, UUID invoiceId) {
        validatePaymentFailure(eventId, occurredAt, invoiceId);
        if (!processedEventPort.claimIfAbsent(eventId, "payment.failed")) {
            return;
        }
        PaymentContribution contribution = paymentContributions.findOrCreateForUpdate(invoiceId);
        LocalDate paymentDate = contribution.getPaymentDate();
        UUID departmentId = contribution.getDepartmentId();
        BigDecimal amount = contribution.getAmount();
        PaymentContributionStatus before = contribution.getStatus();
        PaymentContribution.TransitionEffect effect = contribution.fail(eventId);
        if (before != contribution.getStatus()) {
            paymentContributions.save(contribution);
        }
        if (effect == PaymentContribution.TransitionEffect.REVERSE) {
            updateRevenue(paymentDate, departmentId, amount.negate(), -1);
        }
    }

    private void updateRevenue(LocalDate date, UUID departmentId, BigDecimal amount, int invoiceDelta) {
        updateDailyRevenueScope(date, null, amount);
        if (departmentId != null) {
            updateDailyRevenueScope(date, departmentId, amount);
        }
        updateMonthlyRevenueScope(date, null, amount, invoiceDelta);
        if (departmentId != null) {
            updateMonthlyRevenueScope(date, departmentId, amount, invoiceDelta);
        }
    }

    private void updateDailyRevenueScope(LocalDate date, UUID departmentId, BigDecimal amount) {
        DailyVisitReport daily = dailyReports.findOrCreate(date, departmentId);
        daily.addRevenue(amount);
        dailyReports.save(daily);
    }

    private void updateMonthlyRevenueScope(LocalDate date, UUID departmentId, BigDecimal amount,
                                           int invoiceDelta) {
        var monthly = monthlyReports.findOrCreate(date.getYear(), date.getMonthValue(), departmentId);
        monthly.addRevenue(amount);
        monthly.adjustInvoiceCount(invoiceDelta);
        monthlyReports.save(monthly);
    }

    private void updateDaily(LocalDate reportDate, UUID departmentId,
                             CounterIncrement increment) {
        DailyVisitReport hospital = dailyReports.findOrCreate(reportDate, null);
        increment.apply(hospital, 1);
        dailyReports.save(hospital);

        if (departmentId != null) {
            DailyVisitReport department = dailyReports.findOrCreate(reportDate, departmentId);
            increment.apply(department, 1);
            dailyReports.save(department);
        }
    }

    private void updateDrug(LocalDate reportDate, UUID departmentId, AggregatedItem item) {
        DrugStatistic hospital = drugStatistics.findOrCreate(item.drugId(), item.drugName(), reportDate, null);
        hospital.refreshDrugName(item.drugName());
        hospital.incrementQuantity(item.quantity());
        drugStatistics.save(hospital);

        if (departmentId != null) {
            DrugStatistic department = drugStatistics.findOrCreate(item.drugId(), item.drugName(), reportDate,
                    departmentId);
            department.refreshDrugName(item.drugName());
            department.incrementQuantity(item.quantity());
            drugStatistics.save(department);
        }
    }

    private static void validateOperationalEvent(UUID eventId, LocalDate reportDate, UUID departmentId) {
        if (eventId == null) {
            throw new ReportRuleException("REPORT_EVENT_ID_REQUIRED", "Mã event là bắt buộc");
        }
        if (reportDate == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Ngày báo cáo là bắt buộc");
        }
        if (departmentId == null) {
            throw new ReportRuleException("REPORT_DEPARTMENT_ID_REQUIRED",
                    "Event vận hành phải có mã khoa");
        }
    }

    private static LocalDate validatePrescriptionEvent(UUID eventId, Instant occurredAt, UUID departmentId,
                                                       UUID prescriptionId, List<DispensedItem> items) {
        if (eventId == null) {
            throw new ReportRuleException("REPORT_EVENT_ID_REQUIRED", "Mã event là bắt buộc");
        }
        if (occurredAt == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Thời điểm event là bắt buộc");
        }
        if (departmentId == null) {
            throw new ReportRuleException("REPORT_DEPARTMENT_ID_REQUIRED",
                    "Event vận hành phải có mã khoa");
        }
        if (prescriptionId == null) {
            throw new ReportRuleException("REPORT_PRESCRIPTION_ID_REQUIRED",
                    "Mã đơn thuốc là bắt buộc");
        }
        if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item == null)) {
            throw new ReportRuleException("REPORT_DRUG_ITEMS_REQUIRED",
                    "Đơn thuốc phải có ít nhất một mặt hàng hợp lệ");
        }
        return occurredAt.atZone(REPORT_ZONE).toLocalDate();
    }

    private static LocalDate validatePaymentCompleted(UUID eventId, Instant occurredAt, UUID invoiceId,
                                                      BigDecimal amount) {
        if (eventId == null) {
            throw new ReportRuleException("REPORT_EVENT_ID_REQUIRED", "Mã event là bắt buộc");
        }
        if (occurredAt == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Thời điểm event là bắt buộc");
        }
        if (invoiceId == null) {
            throw new ReportRuleException("REPORT_INVOICE_ID_REQUIRED", "Mã hóa đơn là bắt buộc");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReportRuleException("REPORT_AMOUNT_INVALID", "Số tiền thanh toán phải lớn hơn 0");
        }
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ReportRuleException("REPORT_AMOUNT_SCALE_INVALID",
                    "Số tiền thanh toán chỉ được có tối đa 2 chữ số thập phân");
        }
        return occurredAt.atZone(REPORT_ZONE).toLocalDate();
    }

    private static void validatePaymentFailure(UUID eventId, Instant occurredAt, UUID invoiceId) {
        if (eventId == null) {
            throw new ReportRuleException("REPORT_EVENT_ID_REQUIRED", "Mã event là bắt buộc");
        }
        if (occurredAt == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Thời điểm event là bắt buộc");
        }
        if (invoiceId == null) {
            throw new ReportRuleException("REPORT_INVOICE_ID_REQUIRED", "Mã hóa đơn là bắt buộc");
        }
    }

    private static List<AggregatedItem> groupItems(List<DispensedItem> items) {
        Map<UUID, AggregatedItem> grouped = new HashMap<>();
        for (DispensedItem item : items) {
            AggregatedItem current = grouped.get(item.drugId());
            if (current == null) {
                grouped.put(item.drugId(), new AggregatedItem(item.drugId(), item.drugName(), item.quantity()));
            } else {
                try {
                    grouped.put(item.drugId(), new AggregatedItem(item.drugId(), item.drugName(),
                            Math.addExact(current.quantity(), item.quantity())));
                } catch (ArithmeticException ex) {
                    throw new ReportRuleException("REPORT_DRUG_QUANTITY_INVALID",
                            "Tổng số lượng thuốc vượt giới hạn");
                }
            }
        }
        return new ArrayList<>(grouped.values()).stream()
                .sorted(Comparator.comparing(AggregatedItem::drugId))
                .toList();
    }

    @FunctionalInterface
    private interface CounterIncrement {
        void apply(DailyVisitReport report, int delta);
    }

    private record AggregatedItem(UUID drugId, String drugName, int quantity) {}
}
