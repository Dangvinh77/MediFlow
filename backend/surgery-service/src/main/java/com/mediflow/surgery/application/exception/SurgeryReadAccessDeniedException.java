package com.mediflow.surgery.application.exception;

import com.mediflow.common.exception.BusinessRuleException;

public final class SurgeryReadAccessDeniedException extends BusinessRuleException {
    public SurgeryReadAccessDeniedException() {
        super("SURGERY_READ_SCOPE_DENIED", "Current staff department authority required");
    }
}
