package com.mediflow.inpatient.domain.exception;

public class AdmissionRuleViolationException extends RuntimeException {

    private final String code;

    public AdmissionRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
