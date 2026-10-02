package com.mediflow.report.application.port.in;

import java.time.LocalDate;
import java.util.UUID;
import com.mediflow.report.application.dto.response.OperationalSnapshotReportDTO;

public interface ReadOperationalSnapshotUseCase {
    OperationalSnapshotReportDTO daily(LocalDate from, LocalDate to, UUID departmentId);
    OperationalSnapshotReportDTO surgery(LocalDate from, LocalDate to, UUID departmentId);
}
