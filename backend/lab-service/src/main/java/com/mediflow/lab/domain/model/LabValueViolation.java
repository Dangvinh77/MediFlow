package com.mediflow.lab.domain.model;

/** A template rule broken by an entered value. */
public record LabValueViolation(String fieldCode, String code) {
    public static final String REQUIRED_MISSING = "REQUIRED_MISSING";
    public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    public static final String DUPLICATE_FIELD = "DUPLICATE_FIELD";
    public static final String OPTION_INVALID = "OPTION_INVALID";
    public static final String NUMBER_INVALID = "NUMBER_INVALID";
    public static final String OPERATOR_NOT_ALLOWED = "OPERATOR_NOT_ALLOWED";
    public static final String VALUE_AND_REASON = "VALUE_AND_REASON";
    public static final String VALUE_TOO_LONG = "VALUE_TOO_LONG";
}
