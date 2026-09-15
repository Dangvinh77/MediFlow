package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Daily hospital activity projection, optionally scoped to one department. */
public final class DailyVisitReport {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);

    private final UUID reportId;
    private final LocalDate reportDate;
    private final UUID departmentId;
    private int visitCount;
    private int labCount;
    private int prescriptionCount;
    private BigDecimal revenue;
    private final Instant createdAt;
    private Instant updatedAt;

    private DailyVisitReport(UUID reportId, LocalDate reportDate, UUID departmentId,
                             int visitCount, int labCount, int prescriptionCount,
                             BigDecimal revenue, Instant createdAt, Instant updatedAt) {
        this.reportId = reportId;
        this.reportDate = reportDate;
        this.departmentId = departmentId;
        this.visitCount = visitCount;
        this.labCount = labCount;
        this.prescriptionCount = prescriptionCount;
        this.revenue = normalizeMoney(revenue);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Creates an empty projection row for a business date and department scope. */
    public static DailyVisitReport initialize(LocalDate reportDate, UUID departmentId) {
        if (reportDate == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Ngày báo cáo là bắt buộc");
        }
        return new DailyVisitReport(null, reportDate, departmentId, 0, 0, 0,
                ZERO_MONEY, null, null);
    }

    /** Rehydrates a persisted row without replaying creation-time rules. */
    public static DailyVisitReport restore(UUID reportId, LocalDate reportDate, UUID departmentId,
                                           int visitCount, int labCount, int prescriptionCount,
                                           BigDecimal revenue, Instant createdAt, Instant updatedAt) {
        if (reportDate == null || revenue == null) {
            throw new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                    "Dữ liệu báo cáo ngày đã lưu không hợp lệ");
        }
        if (visitCount < 0 || labCount < 0 || prescriptionCount < 0
                || revenue.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                    "Bộ đếm báo cáo ngày không được âm");
        }
        return new DailyVisitReport(reportId, reportDate, departmentId, visitCount, labCount,
                prescriptionCount, revenue, createdAt, updatedAt);
    }

    /** Adds one or more visits; operational counters accept positive deltas only. */
    public void incrementVisits(int delta) {
        visitCount = addPositiveCounter(visitCount, delta, "REPORT_VISIT_DELTA_INVALID");
    }

    /** Adds one or more completed lab results. */
    public void incrementLabs(int delta) {
        labCount = addPositiveCounter(labCount, delta, "REPORT_LAB_DELTA_INVALID");
    }

    /** Adds one or more filled prescriptions. */
    public void incrementPrescriptions(int delta) {
        prescriptionCount = addPositiveCounter(prescriptionCount, delta,
                "REPORT_PRESCRIPTION_DELTA_INVALID");
    }

    /** Applies a signed money contribution; compensation may be negative but final revenue may not. */
    public void addRevenue(BigDecimal amount) {
        BigDecimal normalized = normalizeMoney(amount);
        BigDecimal next = revenue.add(normalized).setScale(2, RoundingMode.UNNECESSARY);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReportRuleException("REPORT_REVENUE_NEGATIVE",
                    "Doanh thu báo cáo không được âm");
        }
        revenue = next;
    }

    private static int addPositiveCounter(int current, int delta, String code) {
        if (delta <= 0) {
            throw new ReportRuleException(code, "Delta bộ đếm phải lớn hơn 0");
        }
        long next = (long) current + delta;
        if (next > Integer.MAX_VALUE) {
            throw new ReportRuleException(code, "Bộ đếm báo cáo vượt giới hạn");
        }
        return (int) next;
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw new ReportRuleException("REPORT_AMOUNT_REQUIRED", "Số tiền báo cáo là bắt buộc");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ReportRuleException("REPORT_AMOUNT_SCALE_INVALID",
                    "Số tiền báo cáo chỉ được có tối đa 2 chữ số thập phân");
        }
    }

    public UUID getReportId() { return reportId; }
    public LocalDate getReportDate() { return reportDate; }
    public UUID getDepartmentId() { return departmentId; }
    public int getVisitCount() { return visitCount; }
    public int getLabCount() { return labCount; }
    public int getPrescriptionCount() { return prescriptionCount; }
    public BigDecimal getRevenue() { return revenue; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
