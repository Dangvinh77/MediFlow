package com.mediflow.report.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import com.mediflow.report.domain.model.OperationalContribution.Metric;

/** Explicit accepted coverage of an immutable replay snapshot, NOT a live catch-up watermark. */
public record OperationalReportPublication(UUID generationId, LocalDate coveredFrom, LocalDate coveredTo,
        String zoneId, Set<Metric> metrics, Instant reconciledAt, String acceptanceReference) {
    public OperationalReportPublication {
        if (generationId == null || coveredFrom == null || coveredTo == null || coveredTo.isBefore(coveredFrom)
                || zoneId == null || zoneId.isBlank() || metrics == null || metrics.isEmpty()
                || reconciledAt == null || acceptanceReference == null || acceptanceReference.isBlank()) {
            throw new IllegalArgumentException("Complete accepted snapshot coverage is required");
        }
        metrics = Set.copyOf(metrics);
    }
    public boolean covers(LocalDate from, LocalDate to, Set<Metric> required, String configuredZone) {
        return !from.isBefore(coveredFrom) && !to.isAfter(coveredTo)
                && metrics.containsAll(required) && zoneId.equals(configuredZone);
    }
}
