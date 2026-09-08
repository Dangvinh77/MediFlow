package com.mediflow.lab.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Business rule violation owned by the lab bounded context. */
public class LabRuleException extends BusinessRuleException {

    public LabRuleException(String code, String message) {
        super(code, message);
    }
}
