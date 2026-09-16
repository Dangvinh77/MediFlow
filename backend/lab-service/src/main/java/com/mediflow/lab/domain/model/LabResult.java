package com.mediflow.lab.domain.model;

import java.util.UUID;

import com.mediflow.lab.domain.exception.LabRuleException;

/** One textual observation belonging to a {@link LabTest}. */
public final class LabResult {

    private final UUID resultId;
    private final String indicator;
    private final String value;
    private final String unit;
    private final String referenceRange;

    private LabResult(UUID resultId, String indicator, String value, String unit, String referenceRange) {
        this.resultId = resultId;
        this.indicator = indicator;
        this.value = value;
        this.unit = unit;
        this.referenceRange = referenceRange;
    }

    /** Creates a new result. Values remain strings because lab output is not necessarily numeric. */
    public static LabResult create(String indicator, String value, String unit, String referenceRange) {
        if (indicator == null || indicator.isBlank()) {
            throw new LabRuleException("LAB_INDICATOR_REQUIRED", "Chỉ số xét nghiệm không được để trống");
        }
        if (value == null || value.isBlank()) {
            throw new LabRuleException("LAB_VALUE_REQUIRED", "Giá trị xét nghiệm không được để trống");
        }
        return new LabResult(UUID.randomUUID(), indicator, value, unit, referenceRange);
    }

    /** Rehydrates a result already persisted without re-running creation rules. */
    public static LabResult restore(UUID resultId, String indicator, String value,
                                    String unit, String referenceRange) {
        return new LabResult(resultId, indicator, value, unit, referenceRange);
    }

    public UUID getResultId() {
        return resultId;
    }

    public String getIndicator() {
        return indicator;
    }

    public String getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getReferenceRange() {
        return referenceRange;
    }
}
