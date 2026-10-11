package com.mediflow.report.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Aggregate-only response; the named as-of instants make finite coverage distinguishable from live freshness. */
public record FinancialSnapshotReportDTO(LocalDate from, LocalDate to, UUID departmentId, String currency,
        String reportZone, UUID generationId, Instant sourceObservedAt, Instant reconciledAt, Instant publishedAt,
        boolean snapshotOnly, List<Period> periods) {
    public FinancialSnapshotReportDTO { periods=List.copyOf(periods); }
    public record Period(LocalDate from, LocalDate to, BigDecimal cashReceived, BigDecimal depositLiability,
            BigDecimal earnedRevenue, BigDecimal refunds, BigDecimal outstandingReceivable) { }
}
