package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.ReportCapabilitiesDTO;
import com.mediflow.report.application.dto.response.ReportCoverageDTO;
import java.time.LocalDate;

public interface InspectReportCapabilitiesUseCase {
    ReportCapabilitiesDTO capabilities();
    ReportCoverageDTO coverage(LocalDate from, LocalDate to, String currency, String reportZone);
}
