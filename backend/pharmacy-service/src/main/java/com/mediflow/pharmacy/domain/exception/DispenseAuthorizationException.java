package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Permission denial is not a stock failure and must never trigger compensation. */
public class DispenseAuthorizationException extends BusinessRuleException {
    public DispenseAuthorizationException(String code, String message) {
        super(code, message);
    }
}
