package com.mediflow.lab.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class LabTemplateValidationTest {
    static final LabReferenceRange ADULT_HGB = new LabReferenceRange(null, 6570, null, new BigDecimal("120"),
            new BigDecimal("160"), new BigDecimal("70"), new BigDecimal("200"), false);

    static final LabResultTemplate CBC = new LabResultTemplate("CBC", 1, "Công thức máu", List.of(
            new LabTemplateField("WBC", "Bạch cầu", 1, true, LabValueType.QUANTITATIVE, "G/L", null, false, List.of()),
            new LabTemplateField("HGB", "Huyết sắc tố", 2, true, LabValueType.QUANTITATIVE, "g/L", null, false,
                    List.of(ADULT_HGB)),
            new LabTemplateField("NOTE", "Nhận xét", 3, false, LabValueType.TEXT, null, null, false, List.of())));

    static final LabResultTemplate MIXED = new LabResultTemplate("MIX", 1, "Hỗn hợp", List.of(
            new LabTemplateField("GLU", "Glucose", 1, true, LabValueType.QUANTITATIVE, "mmol/L", null, true, List.of()),
            new LabTemplateField("HBSAG", "HBsAg", 2, true, LabValueType.QUALITATIVE, null,
                    List.of("Âm tính", "Dương tính", "Chưa xác định"), false, List.of()),
            new LabTemplateField("PRO", "Protein niệu", 3, false, LabValueType.SEMI_QUANTITATIVE, null,
                    List.of("-", "+", "++", "+++"), false, List.of())));

    static LabValueInput value(String code, String value) {
        return new LabValueInput(code, null, value, null);
    }

    static List<String> codes(List<LabValueViolation> violations) {
        return violations.stream().map(v -> v.fieldCode() + ":" + v.code()).toList();
    }

    @Test void completeCbcIsValid() {
        assertThat(CBC.validate(List.of(value("WBC", "6.5"), value("HGB", "135")))).isEmpty();
    }

    @Test void cbcMissingRequiredAnalyteIsBlocked() {
        assertThat(codes(CBC.validate(List.of(value("WBC", "6.5")))))
                .containsExactly("HGB:REQUIRED_MISSING");
    }

    @Test void blankValueDoesNotCountAsEntered() {
        assertThat(codes(CBC.validate(List.of(value("WBC", "6.5"), value("HGB", "  ")))))
                .containsExactly("HGB:REQUIRED_MISSING");
    }

    @Test void reasonInsteadOfValueSatisfiesARequiredField() {
        assertThat(CBC.validate(List.of(value("WBC", "6.5"),
                new LabValueInput("HGB", null, null, "Mẫu đông, không đo được")))).isEmpty();
    }

    @Test void valueAndReasonTogetherIsRejected() {
        assertThat(codes(CBC.validate(List.of(value("WBC", "6.5"),
                new LabValueInput("HGB", null, "130", "Mẫu đông")))))
                .containsExactly("HGB:VALUE_AND_REASON");
    }

    @Test void boundedValueNeedsAnOperatorAwareField() {
        assertThat(MIXED.validate(List.of(new LabValueInput("GLU", "<", "0.5", null), value("HBSAG", "Âm tính"))))
                .isEmpty();
        assertThat(codes(CBC.validate(List.of(new LabValueInput("WBC", "<", "0.5", null), value("HGB", "130")))))
                .containsExactly("WBC:OPERATOR_NOT_ALLOWED");
        assertThat(codes(MIXED.validate(List.of(new LabValueInput("GLU", "~", "0.5", null), value("HBSAG", "Âm tính")))))
                .containsExactly("GLU:OPERATOR_NOT_ALLOWED");
    }

    @Test void numberFieldRejectsText() {
        assertThat(codes(CBC.validate(List.of(value("WBC", "cao"), value("HGB", "130")))))
                .containsExactly("WBC:NUMBER_INVALID");
    }

    @Test void qualitativeValueMustBeAConfiguredOption() {
        assertThat(codes(MIXED.validate(List.of(value("GLU", "5.1"), value("HBSAG", "Âm")))))
                .containsExactly("HBSAG:OPTION_INVALID");
        assertThat(MIXED.validate(List.of(value("GLU", "5.1"), value("HBSAG", "Chưa xác định"),
                value("PRO", "++")))).isEmpty();
    }

    @Test void unknownAndDuplicateFieldsAreRejected() {
        assertThat(codes(CBC.validate(List.of(value("WBC", "6.5"), value("WBC", "7"), value("HGB", "130"),
                value("PLT", "250"))))).containsExactly("WBC:DUPLICATE_FIELD", "PLT:UNKNOWN_FIELD");
    }

    @Test void optionalFieldMayStayEmpty() {
        assertThat(CBC.validate(List.of(value("WBC", "6.5"), value("HGB", "130"), value("NOTE", "")))).isEmpty();
    }

    @Test void flagsFollowTheApplicableRange() {
        LabTemplateField hgb = CBC.field("HGB").orElseThrow();
        assertThat(hgb.flag(value("HGB", "135"), "MALE", 9000)).isEqualTo(LabFlag.NORMAL);
        assertThat(hgb.flag(value("HGB", "110"), "MALE", 9000)).isEqualTo(LabFlag.LOW);
        assertThat(hgb.flag(value("HGB", "65"), "MALE", 9000)).isEqualTo(LabFlag.CRITICAL_LOW);
        assertThat(hgb.flag(value("HGB", "210"), "MALE", 9000)).isEqualTo(LabFlag.CRITICAL_HIGH);
    }

    @Test void missingRangeOrBoundedValueIsUndeterminedNotNormal() {
        LabTemplateField hgb = CBC.field("HGB").orElseThrow();
        assertThat(hgb.flag(value("HGB", "135"), "MALE", 100)).isEqualTo(LabFlag.UNDETERMINED);
        assertThat(hgb.flag(value("HGB", "135"), "MALE", null)).isEqualTo(LabFlag.UNDETERMINED);
        assertThat(CBC.field("WBC").orElseThrow().flag(value("WBC", "6"), null, 9000)).isEqualTo(LabFlag.UNDETERMINED);
        assertThat(MIXED.field("GLU").orElseThrow().flag(new LabValueInput("GLU", "<", "0.5", null), null, 9000))
                .isEqualTo(LabFlag.UNDETERMINED);
        assertThat(MIXED.field("HBSAG").orElseThrow().flag(value("HBSAG", "Âm tính"), null, 9000)).isNull();
    }
}
