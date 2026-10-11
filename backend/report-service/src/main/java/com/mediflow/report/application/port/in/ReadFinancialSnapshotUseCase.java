package com.mediflow.report.application.port.in;

import java.time.LocalDate;
import java.util.UUID;
import com.mediflow.report.application.dto.response.FinancialSnapshotReportDTO;

public interface ReadFinancialSnapshotUseCase {
    FinancialSnapshotReportDTO daily(LocalDate from, LocalDate to, UUID departmentId);
    FinancialSnapshotReportDTO monthly(int year, UUID departmentId);
}
