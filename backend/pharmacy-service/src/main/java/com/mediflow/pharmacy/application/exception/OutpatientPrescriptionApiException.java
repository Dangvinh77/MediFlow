package com.mediflow.pharmacy.application.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Stable API mode/activation/idempotency errors at the application boundary. */
public class OutpatientPrescriptionApiException extends BusinessRuleException {
    public OutpatientPrescriptionApiException(String code, String message) {
        super(code, message);
    }
}
