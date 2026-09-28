package com.mediflow.surgery.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Typed domain failure with a stable API error code. */
public final class SurgeryRuleException extends BusinessRuleException {

    public SurgeryRuleException(String code, String message) {
        super(code, message);
    }
}
