package com.mediflow.clinical.domain.model;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Diagnosis {
    private static final Pattern ICD = Pattern.compile("^[A-Z]\\d{2}(\\.\\d{1,2})?$");
    private final UUID diagnosisId;
    private final String diagnosisName;
    private final String description;
    private final String icdCode;

    public static Diagnosis create(String name, String description, String icdCode) {
        return restore(UUID.randomUUID(), name, description, icdCode);
    }

    public static Diagnosis restore(UUID id, String name, String description, String icdCode) {
        if (name == null || name.isBlank()) {
            throw new InvalidClinicalDataException("DIAGNOSIS_NAME_REQUIRED", "Diagnosis name is required");
        }
        if (icdCode != null && !ICD.matcher(icdCode).matches()) {
            throw new InvalidClinicalDataException("DIAGNOSIS_ICD_INVALID", "Invalid ICD code");
        }
        return new Diagnosis(Objects.requireNonNull(id), name, description, icdCode);
    }
}
