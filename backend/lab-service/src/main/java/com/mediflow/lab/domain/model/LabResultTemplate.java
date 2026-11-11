package com.mediflow.lab.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A versioned result entry template; a test keeps the version it was created with. */
public record LabResultTemplate(String templateCode, int version, String name, List<LabTemplateField> fields) {
    static final int MAX_TEXT = 2000;
    static final int MAX_REASON = 500;

    public LabResultTemplate {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public Optional<LabTemplateField> field(String code) {
        return fields.stream().filter(field -> field.fieldCode().equals(code)).findFirst();
    }

    /** Checks entered values against this version. A missing value is never read as zero or negative. */
    public List<LabValueViolation> validate(List<LabValueInput> inputs) {
        List<LabValueViolation> violations = new ArrayList<>();
        Set<String> entered = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (LabValueInput input : inputs == null ? List.<LabValueInput>of() : inputs) {
            String code = input == null ? null : input.fieldCode();
            Optional<LabTemplateField> field = Optional.ofNullable(code).flatMap(this::field);
            if (field.isEmpty()) {
                violations.add(new LabValueViolation(code, LabValueViolation.UNKNOWN_FIELD));
                continue;
            }
            if (!seen.add(code)) {
                violations.add(new LabValueViolation(code, LabValueViolation.DUPLICATE_FIELD));
                continue;
            }
            if (input.hasValue() && input.hasReason()) {
                violations.add(new LabValueViolation(code, LabValueViolation.VALUE_AND_REASON));
                continue;
            }
            if (input.hasReason() && input.noResultReason().strip().length() > MAX_REASON) {
                violations.add(new LabValueViolation(code, LabValueViolation.VALUE_TOO_LONG));
                continue;
            }
            if (input.hasValue()) {
                Optional<LabValueViolation> broken = checkValue(field.get(), input);
                if (broken.isPresent()) {
                    violations.add(broken.get());
                    continue;
                }
            }
            if (input.hasValue() || input.hasReason()) {
                entered.add(code);
            }
        }
        for (LabTemplateField field : fields) {
            boolean alreadyReported = violations.stream().anyMatch(v -> field.fieldCode().equals(v.fieldCode()));
            if (field.required() && !entered.contains(field.fieldCode()) && !alreadyReported) {
                violations.add(new LabValueViolation(field.fieldCode(), LabValueViolation.REQUIRED_MISSING));
            }
        }
        return List.copyOf(violations);
    }

    private static Optional<LabValueViolation> checkValue(LabTemplateField field, LabValueInput input) {
        String code = field.fieldCode();
        String operator = input.operator() == null || input.operator().isBlank() ? null : input.operator().strip();
        String value = input.value().strip();
        if (value.length() > MAX_TEXT) {
            return Optional.of(new LabValueViolation(code, LabValueViolation.VALUE_TOO_LONG));
        }
        if (operator != null && (field.valueType() != LabValueType.QUANTITATIVE || !field.allowsOperator()
                || !LabTemplateField.OPERATORS.contains(operator))) {
            return Optional.of(new LabValueViolation(code, LabValueViolation.OPERATOR_NOT_ALLOWED));
        }
        return switch (field.valueType()) {
            case QUANTITATIVE -> number(value) ? Optional.empty()
                    : Optional.of(new LabValueViolation(code, LabValueViolation.NUMBER_INVALID));
            case QUALITATIVE, SEMI_QUANTITATIVE -> field.allowedOptions().contains(value) ? Optional.empty()
                    : Optional.of(new LabValueViolation(code, LabValueViolation.OPTION_INVALID));
            case TEXT -> Optional.empty();
        };
    }

    private static boolean number(String value) {
        if (value.length() > 32) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }
}
