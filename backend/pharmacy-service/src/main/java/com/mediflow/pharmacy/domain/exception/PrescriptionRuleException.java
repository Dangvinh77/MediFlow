package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

public class PrescriptionRuleException extends BusinessRuleException {
     public PrescriptionRuleException(String code, String message) {
        super(code, message);
    }
}
