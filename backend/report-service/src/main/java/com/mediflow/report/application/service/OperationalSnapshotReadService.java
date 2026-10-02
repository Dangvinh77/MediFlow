package com.mediflow.report.application.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.report.application.dto.response.OperationalSnapshotReportDTO;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadOperationalSnapshotUseCase;
import com.mediflow.report.application.port.out.OperationalSnapshotReadPort;
import com.mediflow.report.domain.model.OperationalContribution.Metric;

/** Zero-fill only inside explicitly accepted finite coverage, not merely because a row is absent. */
@Service
@Transactional(readOnly = true)
public class OperationalSnapshotReadService implements ReadOperationalSnapshotUseCase {
    private static final Set<Metric> DAILY = Set.of(Metric.COMPLETED_VISITS, Metric.ADMISSIONS, Metric.DISCHARGES,
            Metric.LAB_TESTS, Metric.DISPENSED_PRESCRIPTIONS, Metric.DISPENSED_UNITS);
    private static final Set<Metric> SURGERY = Set.of(Metric.SURGERIES_COMPLETED, Metric.SURGERIES_CANCELLED, Metric.SURGERY_DURATION_MINUTES);
    private final OperationalSnapshotReadPort store;
    private final ZoneId zone;
    public OperationalSnapshotReadService(OperationalSnapshotReadPort store, ZoneId zone) { this.store = store; this.zone = zone; }

    @Override public OperationalSnapshotReportDTO daily(LocalDate from, LocalDate to, UUID departmentId) {
        return query("DAILY", DAILY, from, to, departmentId);
    }
    @Override public OperationalSnapshotReportDTO surgery(LocalDate from, LocalDate to, UUID departmentId) {
        return query("SURGERY", SURGERY, from, to, departmentId);
    }

    private OperationalSnapshotReportDTO query(String kind, Set<Metric> required, LocalDate from, LocalDate to, UUID departmentId) {
        if (from == null || to == null || to.isBefore(from) || from.getYear() < 2000 || to.getYear() > 2100
                || ChronoUnit.DAYS.between(from, to) >= 366) throw new ReportPeriodValidationException();
        var publication = store.publication(kind).filter(p -> p.covers(from, to, required, zone.getId()))
                .orElseThrow(ReportProjectionUnavailableException::new);
        var ordered = required.stream().sorted(Comparator.naturalOrder()).toList();
        Map<LocalDate, LinkedHashMap<String, Long>> days = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var metrics = new LinkedHashMap<String, Long>();
            ordered.forEach(metric -> metrics.put(metric.wireName(), 0L));
            days.put(date, metrics);
        }
        var seen = new java.util.HashSet<String>();
        for (var value : store.read(publication.generationId(), from, to, departmentId, required)) {
            if (!days.containsKey(value.date()) || !required.contains(value.metric()) || value.value() < 0
                    || !seen.add(value.date() + ":" + value.metric().name())) {
                throw new ReportProjectionUnavailableException();
            }
            days.get(value.date()).put(value.metric().wireName(), value.value());
        }
        var response = new ArrayList<OperationalSnapshotReportDTO.Day>();
        days.forEach((date, metrics) -> response.add(new OperationalSnapshotReportDTO.Day(date, metrics)));
        return new OperationalSnapshotReportDTO(from, to, departmentId, publication.generationId(), publication.reconciledAt(), true, response);
    }
}
