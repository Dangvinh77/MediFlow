package com.mediflow.report.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.report.domain.model.DrugStatistic;
import com.mediflow.report.domain.model.TopMedicineSummary;

/** Persistence boundary for daily medicine quantities and grouped top-medicine queries. */
public interface DrugStatisticRepositoryPort {

    DrugStatistic findOrCreate(UUID drugId, String drugName, LocalDate reportDate, UUID departmentId);

    DrugStatistic save(DrugStatistic statistic);

    List<TopMedicineSummary> topMedicines(LocalDate fromDate, LocalDate toDate,
                                           UUID departmentId, int limit);
}
