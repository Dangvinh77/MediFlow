package com.mediflow.report.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.report.domain.model.DailyVisitReport;

/** Persistence boundary for daily activity projections. */
public interface DailyVisitReportRepositoryPort {

    DailyVisitReport findOrCreate(LocalDate reportDate, UUID departmentId);

    DailyVisitReport save(DailyVisitReport report);

    Optional<DailyVisitReport> find(LocalDate reportDate, UUID departmentId);

    List<DailyVisitReport> findRange(LocalDate fromDate, LocalDate toDate, UUID departmentId);
}
