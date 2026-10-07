package com.mediflow.report.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;
import com.mediflow.report.application.port.in.ReadReportTelemetryUseCase;
import com.mediflow.report.application.port.out.ReportTelemetryReadPort;

@Service
public class ReportTelemetryApplicationService implements ReadReportTelemetryUseCase {
    private final ReportTelemetryReadPort store;

    public ReportTelemetryApplicationService(ReportTelemetryReadPort store) { this.store = store; }

    /** X-01.6: one bounded read transaction, no source claims or projection effects. */
    @Override
    @Transactional(readOnly = true, timeout = 5)
    public ReportTelemetrySnapshot capture() { return store.capture(); }
}
