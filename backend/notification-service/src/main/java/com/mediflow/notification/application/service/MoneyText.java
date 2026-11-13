package com.mediflow.notification.application.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Vietnamese money wording for patient-facing text: 36.000 VND, 1.250.500,5 VND. */
public final class MoneyText {
    private MoneyText() { }

    /** The amount alone, grouped with dots; fractional digits only when not zero. */
    public static String amount(BigDecimal value) {
        if (value == null) return "";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        format.setParseBigDecimal(true);
        return format.format(value.stripTrailingZeros());
    }

    public static String of(BigDecimal value, String currency) {
        String unit = currency == null || currency.isBlank() ? "VND" : currency.trim();
        return amount(value) + " " + unit;
    }
}
