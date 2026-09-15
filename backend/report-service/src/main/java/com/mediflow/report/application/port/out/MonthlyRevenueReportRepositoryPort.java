package com.mediflow.report.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.report.domain.model.MonthlyRevenueReport;

/** Persistence boundary for monthly revenue projections. */
public interface MonthlyRevenueReportRepositoryPort {

    MonthlyRevenueReport findOrCreate(int year, int month, UUID departmentId);

    MonthlyRevenueReport save(MonthlyRevenueReport report);

    Optional<MonthlyRevenueReport> find(int year, int month, UUID departmentId);
}
