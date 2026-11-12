package com.mediflow.lab.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** One field of a versioned result template. */
public record LabTemplateField(String fieldCode, String label, int position, boolean required, LabValueType valueType,
        String unit, List<String> allowedOptions, boolean allowsOperator, List<LabReferenceRange> ranges) {

    public static final Set<String> OPERATORS = Set.of("<", "<=", ">", ">=");

    public LabTemplateField {
        allowedOptions = allowedOptions == null ? List.of() : List.copyOf(allowedOptions);
        ranges = ranges == null ? List.of() : List.copyOf(ranges);
    }

    /** Null for non-numeric fields; {@link LabFlag#UNDETERMINED} whenever the value cannot be classified. */
    public LabFlag flag(LabValueInput input, String patientSex, Integer ageDays) {
        if (valueType != LabValueType.QUANTITATIVE) {
            return null;
        }
        if (input == null || !input.hasValue() || input.operator() != null && !input.operator().isBlank()) {
            return LabFlag.UNDETERMINED;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(input.value().strip());
        } catch (NumberFormatException invalid) {
            return LabFlag.UNDETERMINED;
        }
        LabReferenceRange range = rangeFor(patientSex, ageDays);
        return range == null ? LabFlag.UNDETERMINED : range.flag(value);
    }

    /** The applicable range kept with a released value, or null when none applies. */
    public LabReferenceRange rangeFor(String patientSex, Integer ageDays) {
        return ranges.stream().filter(range -> range.appliesTo(patientSex, ageDays)).findFirst().orElse(null);
    }
}
