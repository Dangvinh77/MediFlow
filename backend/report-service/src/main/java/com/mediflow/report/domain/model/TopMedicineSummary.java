package com.mediflow.report.domain.model;

import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Immutable query projection returned by the top-medicine repository port. */
public record TopMedicineSummary(UUID drugId, String drugName, int totalQuantity) {

    public TopMedicineSummary {
        if (drugId == null) {
            throw new ReportRuleException("REPORT_DRUG_ID_REQUIRED", "Mã thuốc là bắt buộc");
        }
        if (drugName == null || drugName.isBlank()) {
            throw new ReportRuleException("REPORT_DRUG_NAME_REQUIRED", "Tên thuốc là bắt buộc");
        }
        if (totalQuantity < 0) {
            throw new ReportRuleException("REPORT_DRUG_QUANTITY_INVALID",
                    "Tổng số lượng thuốc không được âm");
        }
        drugName = drugName.trim();
    }
}
