package com.mediflow.report.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;

/** Driving use case for the three read-only report queries. */
public interface ReadReportUseCase {

    DailyReportDTO daily(LocalDate date, UUID departmentId);

    MonthlyReportDTO monthly(int month, int year, UUID departmentId);

    List<TopMedicineDTO> topMedicines(LocalDate fromDate, LocalDate toDate,
                                      UUID departmentId, int limit);
}
