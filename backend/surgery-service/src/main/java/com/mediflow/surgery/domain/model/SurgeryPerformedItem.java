package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.math.BigDecimal;
import java.util.UUID;

/** Actual performed line sent to Billing for catalog resolution; Surgery never stores an amount. */
public record SurgeryPerformedItem(
        UUID performedItemId,
        String itemCode,
        String priceCode,
        BigDecimal quantity) {

    public SurgeryPerformedItem {
        if (performedItemId == null || !validCode(itemCode) || !validCode(priceCode)
                || quantity == null || quantity.signum() <= 0) {
            throw invalid("SURGERY_PERFORMED_ITEM_INVALID");
        }
        itemCode = itemCode.trim();
        priceCode = priceCode.trim();
        quantity = quantity.stripTrailingZeros();
    }

    private static boolean validCode(String value) {
        return value != null && !value.isBlank() && value.length() <= 64;
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Mục đã thực hiện phải có mã và số lượng dương");
    }
}
