package com.mediflow.report.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.domain.model.OperationalReportPublication;

/** Publication and immutable generation values only; no query against another service. */
public interface OperationalSnapshotReadPort {
    Optional<OperationalReportPublication> publication(String reportKind);
    List<Value> read(UUID generationId, LocalDate from, LocalDate to, UUID departmentId, Set<Metric> metrics);
    record Value(LocalDate date, Metric metric, long value) { }
}
