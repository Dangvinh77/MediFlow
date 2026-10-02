package com.mediflow.report.application.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

/** Unavailable source/projection is never an all-zero report. */
public class ReportProjectionUnavailableException extends ResourceNotFoundException {
    public ReportProjectionUnavailableException() {
        super("REPORT_NOT_FOUND", "Requested projection does not have accepted coverage");
    }
}
