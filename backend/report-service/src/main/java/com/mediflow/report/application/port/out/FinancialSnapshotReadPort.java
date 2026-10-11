package com.mediflow.report.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mediflow.report.domain.model.FinancialAmounts;
import com.mediflow.report.domain.model.FinancialReportPublication;

public interface FinancialSnapshotReadPort {
    Optional<FinancialReportPublication> publication(String currency, String reportZone);
    List<Day> read(UUID generationId, LocalDate from, LocalDate to, UUID departmentId);
    record Day(LocalDate date, FinancialAmounts amounts) { }
}
