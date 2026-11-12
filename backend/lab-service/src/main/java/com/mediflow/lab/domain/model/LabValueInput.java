package com.mediflow.lab.domain.model;

/** One entered value; {@code noResultReason} explains a value that could not be produced. */
public record LabValueInput(String fieldCode, String operator, String value, String noResultReason) {

    public boolean hasValue() {
        return value != null && !value.isBlank();
    }

    public boolean hasReason() {
        return noResultReason != null && !noResultReason.isBlank();
    }
}
