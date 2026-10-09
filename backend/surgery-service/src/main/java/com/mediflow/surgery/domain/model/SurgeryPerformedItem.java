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
        // Preserve the same value in owned NUMERIC(19,4) storage and the Billing fact.
        // Normalize only insignificant zeroes; never round a clinical quantity to fit storage.
        try {
            quantity = quantity.stripTrailingZeros();
        } catch (ArithmeticException unsupportedScale) {
            throw invalid("SURGERY_PERFORMED_ITEM_INVALID");
        }
        if (quantity.scale() > 4 || (long) quantity.precision() - quantity.scale() > 15) {
            throw invalid("SURGERY_PERFORMED_ITEM_INVALID");
        }
        itemCode = itemCode.trim();
        priceCode = priceCode.trim();
    }

    private static boolean validCode(String value) {
        return value != null && !value.isBlank() && value.length() <= 64;
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Mục đã thực hiện phải có mã và số lượng dương");
    }
}
