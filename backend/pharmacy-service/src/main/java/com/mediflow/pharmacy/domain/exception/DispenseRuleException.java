package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

public class DispenseRuleException extends BusinessRuleException {
    public DispenseRuleException(String code, String message) {
        super(code, message);
    }
}
