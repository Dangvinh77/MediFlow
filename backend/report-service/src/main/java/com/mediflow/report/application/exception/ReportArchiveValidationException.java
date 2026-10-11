package com.mediflow.report.application.exception;

import com.mediflow.common.exception.BusinessRuleException;

public class ReportArchiveValidationException extends BusinessRuleException {
    public ReportArchiveValidationException() {
        super("REPORT_FINANCIAL_ARCHIVE_INVALID", "Archive body must contain 1 to 33554432 bytes");
    }
}
