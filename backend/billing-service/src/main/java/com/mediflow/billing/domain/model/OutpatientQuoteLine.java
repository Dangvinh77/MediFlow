package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

/** Immutable server-issued catalogue and support-policy snapshot. Support is never cash. */
public record OutpatientQuoteLine(UUID sourceId, String sourceType, String priceCode,
        String catalogueVersion, String policyVersion, String description, BigDecimal quantity,
        BigDecimal unitPrice, BigDecimal grossAmount, BigDecimal supportRate,
        BigDecimal supportAmount, BigDecimal patientPayable) {
    public OutpatientQuoteLine {
        if (sourceId == null || sourceType == null || !Set.of("EXAM", "LAB_TEST", "PRESCRIPTION").contains(sourceType)
                || blank(priceCode) || blank(catalogueVersion) || blank(policyVersion) || blank(description)
                || quantity == null || quantity.signum() <= 0 || unitPrice == null || unitPrice.signum() < 0
                || supportRate == null || supportRate.signum() < 0 || supportRate.compareTo(BigDecimal.ONE) > 0
                || grossAmount == null || supportAmount == null || patientPayable == null)
            throw new IllegalArgumentException("Invalid outpatient quote snapshot");
        var expectedGross = unitPrice.multiply(quantity);
        var expectedSupport = expectedGross.multiply(supportRate).setScale(0, RoundingMode.HALF_UP);
        if (grossAmount.compareTo(expectedGross) != 0 || supportAmount.compareTo(expectedSupport) != 0
                || supportAmount.signum() < 0 || supportAmount.compareTo(grossAmount) > 0
                || patientPayable.compareTo(grossAmount.subtract(supportAmount)) != 0)
            throw new IllegalArgumentException("Outpatient quote amounts do not balance");
    }

    public static OutpatientQuoteLine calculate(UUID sourceId, String sourceType, String priceCode,
            String catalogueVersion, String policyVersion, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal supportRate) {
        if (quantity == null || unitPrice == null || supportRate == null)
            throw new IllegalArgumentException("Quote quantity, price and rate are required");
        var gross = unitPrice.multiply(quantity);
        var support = gross.multiply(supportRate).setScale(0, RoundingMode.HALF_UP);
        return new OutpatientQuoteLine(sourceId, sourceType, priceCode, catalogueVersion, policyVersion,
                description, quantity, unitPrice, gross, supportRate, support, gross.subtract(support));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
