package com.mediflow.report.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Aggregate snapshot only; no patient IDs, narrative, contact, consent or result payloads. */
public record OperationalSnapshotReportDTO(LocalDate from, LocalDate to, UUID departmentId, UUID generationId,
        Instant reconciledAt, boolean snapshotOnly, List<Day> days) {
    public OperationalSnapshotReportDTO { days = List.copyOf(days); }
    public record Day(LocalDate date, Map<String, Long> metrics) {
        public Day { metrics = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metrics)); }
    }
}
