package com.mediflow.clinical.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** A clinical invariant failed; mapped to HTTP 422 by the future web adapter. */
public class InvalidClinicalDataException extends BusinessRuleException {
    public InvalidClinicalDataException(String code, String message) {
        super(code, message);
    }
}
