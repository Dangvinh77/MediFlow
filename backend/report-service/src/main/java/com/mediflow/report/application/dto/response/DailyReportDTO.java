package com.mediflow.report.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** HTTP representation of one daily report scope. */
public record DailyReportDTO(
        LocalDate reportDate,
        UUID departmentId,
        int visitCount,
        int labCount,
        int prescriptionCount,
        BigDecimal revenue
) {}
