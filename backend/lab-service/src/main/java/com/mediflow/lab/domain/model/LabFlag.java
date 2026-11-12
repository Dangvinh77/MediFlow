package com.mediflow.lab.domain.model;

/** Interpretation of a quantitative value against the configured reference range. */
public enum LabFlag {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL_LOW,
    CRITICAL_HIGH,
    /** No applicable range, a bounded value such as "< 0.5", or no value at all: never shown as normal. */
    UNDETERMINED;

    public boolean critical() {
        return this == CRITICAL_LOW || this == CRITICAL_HIGH;
    }
}
