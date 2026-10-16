package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.OutpatientReportDTO;
import java.time.LocalDate;
import java.util.UUID;

public interface ReadOutpatientReportUseCase {
    OutpatientReportDTO daily(LocalDate from,LocalDate to,UUID departmentId);
    OutpatientReportDTO monthly(int year,UUID departmentId);
}
