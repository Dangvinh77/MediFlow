package com.mediflow.report.application.dto.command;

import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Immutable application value carrying one dispensed medicine from the event adapter. */
public record DispensedItem(UUID drugId, String drugName, int quantity) {

    public DispensedItem {
        if (drugId == null) {
            throw new ReportRuleException("REPORT_DRUG_ID_REQUIRED", "Mã thuốc là bắt buộc");
        }
        if (drugName == null || drugName.isBlank()) {
            throw new ReportRuleException("REPORT_DRUG_NAME_REQUIRED", "Tên thuốc là bắt buộc");
        }
        if (quantity <= 0) {
            throw new ReportRuleException("REPORT_DRUG_QUANTITY_INVALID",
                    "Số lượng thuốc phải lớn hơn 0");
        }
        drugName = drugName.trim();
    }
}
