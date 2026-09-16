package com.mediflow.report.application.dto.command;

import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Immutable application value carrying one dispensed medicine from the event adapter. */
public record DispensedItem(UUID drugId, String drugName, int quantity) {

    private static final int MAX_DRUG_NAME_LENGTH = 150;

    public DispensedItem {
        if (drugId == null) {
            throw new ReportRuleException("REPORT_DRUG_ID_REQUIRED", "Mã thuốc là bắt buộc");
        }
        if (drugName == null || drugName.isBlank()) {
            throw new ReportRuleException("REPORT_DRUG_NAME_REQUIRED", "Tên thuốc là bắt buộc");
        }
        String normalizedName = drugName.trim();
        if (normalizedName.length() > MAX_DRUG_NAME_LENGTH) {
            throw new ReportRuleException("REPORT_DRUG_NAME_TOO_LONG",
                    "Tên thuốc không được vượt quá " + MAX_DRUG_NAME_LENGTH + " ký tự");
        }
        if (quantity <= 0) {
            throw new ReportRuleException("REPORT_DRUG_QUANTITY_INVALID",
                    "Số lượng thuốc phải lớn hơn 0");
        }
        drugName = normalizedName;
    }
}
