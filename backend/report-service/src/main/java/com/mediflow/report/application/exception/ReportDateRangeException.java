package com.mediflow.report.application.exception;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Raised when a report query contains an invalid inclusive date range. */
public final class ReportDateRangeException extends ReportRuleException {

    public static final String CODE = "REPORT_DATE_RANGE_INVALID";

    public ReportDateRangeException(String message) {
        super(CODE, message);
    }
}
