package com.mediflow.report.application.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** HTTP representation of a monthly report with a zero-filled calendar detail. */
public record MonthlyReportDTO(
        int month,
        int year,
        UUID departmentId,
        BigDecimal totalRevenue,
        int invoiceCount,
        List<DailyReportDTO> dailyDetails
) {

    public MonthlyReportDTO {
        dailyDetails = dailyDetails == null ? List.of() : List.copyOf(dailyDetails);
    }
}
