package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;

public interface ReportTelemetryReadPort {
    ReportTelemetrySnapshot capture();
}
