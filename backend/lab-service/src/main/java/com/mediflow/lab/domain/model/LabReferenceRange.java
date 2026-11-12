package com.mediflow.lab.domain.model;

import java.math.BigDecimal;

/**
 * A configured reference interval for one field. {@code reviewed} is false for ranges no clinical
 * laboratory has approved, such as the demo catalogue.
 */
public record LabReferenceRange(String sex, Integer ageMinDays, Integer ageMaxDays, BigDecimal low, BigDecimal high,
        BigDecimal criticalLow, BigDecimal criticalHigh, boolean reviewed) {

    /** Inclusive lower age, exclusive upper age; a range with age bounds never applies to an unknown age. */
    public boolean appliesTo(String patientSex, Integer ageDays) {
        if (sex != null && !sex.equalsIgnoreCase(patientSex == null ? "" : patientSex)) {
            return false;
        }
        if (ageMinDays == null && ageMaxDays == null) {
            return true;
        }
        if (ageDays == null) {
            return false;
        }
        return (ageMinDays == null || ageDays >= ageMinDays) && (ageMaxDays == null || ageDays < ageMaxDays);
    }

    public LabFlag flag(BigDecimal value) {
        if (value == null) {
            return LabFlag.UNDETERMINED;
        }
        if (criticalLow != null && value.compareTo(criticalLow) < 0) {
            return LabFlag.CRITICAL_LOW;
        }
        if (criticalHigh != null && value.compareTo(criticalHigh) > 0) {
            return LabFlag.CRITICAL_HIGH;
        }
        if (low == null && high == null) {
            return LabFlag.UNDETERMINED;
        }
        if (low != null && value.compareTo(low) < 0) {
            return LabFlag.LOW;
        }
        if (high != null && value.compareTo(high) > 0) {
            return LabFlag.HIGH;
        }
        return LabFlag.NORMAL;
    }
}
