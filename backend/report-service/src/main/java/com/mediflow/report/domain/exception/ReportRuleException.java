package com.mediflow.report.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Raised when a report projection would violate a domain invariant. */
public class ReportRuleException extends BusinessRuleException {

    public ReportRuleException(String code, String message) {
        super(code, message);
    }
}
