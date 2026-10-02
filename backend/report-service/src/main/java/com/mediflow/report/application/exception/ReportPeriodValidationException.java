package com.mediflow.report.application.exception;

import com.mediflow.common.exception.BusinessRuleException;

public class ReportPeriodValidationException extends BusinessRuleException {
    public ReportPeriodValidationException() {
        super("REPORT_VALIDATION_ERROR", "Inclusive report period must contain 1 to 366 days in years 2000 to 2100");
    }
}
