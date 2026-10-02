package com.mediflow.patient.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** A patient domain invariant that is not a request-shape validation failure. */
public class InvalidPatientDataException extends BusinessRuleException {

    public InvalidPatientDataException(String code, String message) {
        super(code, message);
    }
}
