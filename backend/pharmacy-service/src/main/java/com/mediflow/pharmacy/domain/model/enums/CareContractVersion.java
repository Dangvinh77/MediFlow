package com.mediflow.pharmacy.domain.model.enums;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;

public enum CareContractVersion {
    LEGACY(0),
    V1(1);

    private final int value;

    CareContractVersion(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static CareContractVersion from(int value) {
        return switch (value) {
            case 0 -> LEGACY;
            case 1 -> V1;
            default -> throw invalid("Unsupported care contract version");
        };
    }

    private static PrescriptionRuleException invalid(String message) {
        return new PrescriptionRuleException("PHARMACY_CARE_CONTEXT_INVALID", message);
    }
}
