package com.mediflow.patient.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/**
 * A patient invariant was violated. Maps to HTTP 422.
 *
 * <p>This is deliberately separate from the Bean Validation on the request DTO: that produces a
 * 400 with per-field detail for a malformed HTTP request, while this protects the model from
 * <em>any</em> caller — including an event consumer or a test. Both layers exist on purpose.
 */
public class InvalidPatientDataException extends BusinessRuleException {

    public InvalidPatientDataException(String code, String message) {
        super(code, message);
    }
}
