package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;

/** Internal monitoring only; not a business report or an activation endpoint. */
public interface ReadReportTelemetryUseCase {
    ReportTelemetrySnapshot capture();
}
