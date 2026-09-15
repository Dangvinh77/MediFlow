package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Monthly revenue projection, optionally scoped to one department. */
public final class MonthlyRevenueReport {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);

    private final UUID reportId;
    private final int month;
    private final int year;
    private final UUID departmentId;
    private BigDecimal totalRevenue;
    private int invoiceCount;
    private final Instant createdAt;
    private Instant updatedAt;

    private MonthlyRevenueReport(UUID reportId, int month, int year, UUID departmentId,
                                 BigDecimal totalRevenue, int invoiceCount,
                                 Instant createdAt, Instant updatedAt) {
        this.reportId = reportId;
        this.month = month;
        this.year = year;
        this.departmentId = departmentId;
        this.totalRevenue = normalizeMoney(totalRevenue);
        this.invoiceCount = invoiceCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Creates an empty projection row for a valid calendar month. */
    public static MonthlyRevenueReport initialize(int month, int year, UUID departmentId) {
        validateMonth(month);
        if (year <= 0) {
            throw new ReportRuleException("REPORT_YEAR_INVALID", "Năm báo cáo phải lớn hơn 0");
        }
        return new MonthlyRevenueReport(null, month, year, departmentId, ZERO_MONEY, 0, null, null);
    }

    /** Rehydrates a persisted row while preserving its stored counters. */
    public static MonthlyRevenueReport restore(UUID reportId, int month, int year, UUID departmentId,
                                               BigDecimal totalRevenue, int invoiceCount,
                                               Instant createdAt, Instant updatedAt) {
        validateMonth(month);
        if (year <= 0 || totalRevenue == null || invoiceCount < 0
                || totalRevenue.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                    "Dữ liệu báo cáo doanh thu đã lưu không hợp lệ");
        }
        return new MonthlyRevenueReport(reportId, month, year, departmentId, totalRevenue,
                invoiceCount, createdAt, updatedAt);
    }

    /** Applies a signed revenue contribution; the final total must remain non-negative. */
    public void addRevenue(BigDecimal amount) {
        BigDecimal normalized = normalizeMoney(amount);
        BigDecimal next = totalRevenue.add(normalized).setScale(2, RoundingMode.UNNECESSARY);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReportRuleException("REPORT_REVENUE_NEGATIVE",
                    "Doanh thu tháng không được âm");
        }
        totalRevenue = next;
    }

    /** Adjusts invoice count and clamps a compensation delta at zero. */
    public void adjustInvoiceCount(int delta) {
        long next = (long) invoiceCount + delta;
        if (next < 0) {
            invoiceCount = 0;
        } else if (next > Integer.MAX_VALUE) {
            throw new ReportRuleException("REPORT_INVOICE_COUNT_INVALID",
                    "Số hóa đơn báo cáo vượt giới hạn");
        } else {
            invoiceCount = (int) next;
        }
    }

    private static void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ReportRuleException("REPORT_MONTH_INVALID", "Tháng báo cáo phải từ 1 đến 12");
        }
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
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public UUID getDepartmentId() { return departmentId; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public int getInvoiceCount() { return invoiceCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
