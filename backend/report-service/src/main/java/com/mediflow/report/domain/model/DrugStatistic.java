package com.mediflow.report.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Daily dispensed quantity projection for one drug and department scope. */
public final class DrugStatistic {

    private static final int MAX_DRUG_NAME_LENGTH = 150;

    private final UUID statisticId;
    private final UUID drugId;
    private String drugName;
    private final LocalDate reportDate;
    private final UUID departmentId;
    private int dispensedQuantity;
    private Instant updatedAt;

    private DrugStatistic(UUID statisticId, UUID drugId, String drugName, LocalDate reportDate,
                          UUID departmentId, int dispensedQuantity, Instant updatedAt) {
        this.statisticId = statisticId;
        this.drugId = drugId;
        this.drugName = drugName;
        this.reportDate = reportDate;
        this.departmentId = departmentId;
        this.dispensedQuantity = dispensedQuantity;
        this.updatedAt = updatedAt;
    }

    /** Creates an empty statistic row with a non-blank drug-name snapshot. */
    public static DrugStatistic initialize(UUID drugId, String drugName, LocalDate reportDate,
                                           UUID departmentId) {
        validateIdentity(drugId, drugName, reportDate);
        return new DrugStatistic(null, drugId, drugName.trim(), reportDate, departmentId, 0, null);
    }

    /** Rehydrates a persisted statistic row. */
    public static DrugStatistic restore(UUID statisticId, UUID drugId, String drugName,
                                        LocalDate reportDate, UUID departmentId,
                                        int dispensedQuantity, Instant updatedAt) {
        validateIdentity(drugId, drugName, reportDate);
        if (dispensedQuantity < 0) {
            throw new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                    "Số lượng thuốc đã cấp không được âm");
        }
        return new DrugStatistic(statisticId, drugId, drugName.trim(), reportDate, departmentId,
                dispensedQuantity, updatedAt);
    }

    /** Adds a positive quantity; V1 has no drug-compensation event. */
    public void incrementQuantity(int delta) {
        if (delta <= 0) {
            throw new ReportRuleException("REPORT_DRUG_DELTA_INVALID",
                    "Delta số lượng thuốc phải lớn hơn 0");
        }
        long next = (long) dispensedQuantity + delta;
        if (next > Integer.MAX_VALUE) {
            throw new ReportRuleException("REPORT_DRUG_DELTA_INVALID",
                    "Số lượng thuốc đã cấp vượt giới hạn");
        }
        dispensedQuantity = (int) next;
    }

    /** Refreshes the denormalized drug-name snapshot from the latest source event. */
    public void refreshDrugName(String drugName) {
        this.drugName = validateDrugName(drugName);
    }

    private static void validateIdentity(UUID drugId, String drugName, LocalDate reportDate) {
        if (drugId == null) {
            throw new ReportRuleException("REPORT_DRUG_ID_REQUIRED", "Mã thuốc là bắt buộc");
        }
        validateDrugName(drugName);
        if (reportDate == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Ngày báo cáo là bắt buộc");
        }
    }

    private static String validateDrugName(String drugName) {
        if (drugName == null || drugName.isBlank()) {
            throw new ReportRuleException("REPORT_DRUG_NAME_REQUIRED", "Tên thuốc là bắt buộc");
        }
        String normalized = drugName.trim();
        if (normalized.length() > MAX_DRUG_NAME_LENGTH) {
            throw new ReportRuleException("REPORT_DRUG_NAME_TOO_LONG",
                    "Tên thuốc không được vượt quá " + MAX_DRUG_NAME_LENGTH + " ký tự");
        }
        return normalized;
    }

    public UUID getStatisticId() { return statisticId; }
    public UUID getDrugId() { return drugId; }
    public String getDrugName() { return drugName; }
    public LocalDate getReportDate() { return reportDate; }
    public UUID getDepartmentId() { return departmentId; }
    public int getDispensedQuantity() { return dispensedQuantity; }
    public Instant getUpdatedAt() { return updatedAt; }
}
