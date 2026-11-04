package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.OutpatientStatisticsDTO;
import java.time.LocalDate;
import java.util.UUID;

public interface ReadOutpatientStatisticsUseCase {
    OutpatientStatisticsDTO statistics(LocalDate from, LocalDate to, UUID departmentId);
}
