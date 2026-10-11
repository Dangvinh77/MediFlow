package com.mediflow.report.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Accepted immutable five-metric snapshot. Merely VERIFIED or a populated live table is not publication. */
public record FinancialReportPublication(UUID generationId, LocalDate coveredFrom, LocalDate coveredTo,
        String currency, String reportZone, Instant sourceObservedAt, Instant reconciledAt, Instant publishedAt,
        String acceptanceReference) {
    public FinancialReportPublication {
        if (generationId==null || coveredFrom==null || coveredTo==null || coveredTo.isBefore(coveredFrom)
                || coveredFrom.getYear()<2000 || coveredTo.getYear()>2100
                || currency==null || !currency.matches("[A-Z]{3}") || reportZone==null
                || sourceObservedAt==null || reconciledAt==null || publishedAt==null
                || reconciledAt.isBefore(sourceObservedAt) || publishedAt.isBefore(reconciledAt)
                || acceptanceReference==null || acceptanceReference.isBlank() || acceptanceReference.length()>120) {
            throw new IllegalArgumentException("Complete accepted financial coverage required");
        }
        var zone=ZoneId.of(reportZone);
        if (sourceObservedAt.isBefore(coveredTo.plusDays(1).atStartOfDay(zone).toInstant())) {
            throw new IllegalArgumentException("Accepted financial coverage requires a completed business day");
        }
    }
    public boolean covers(LocalDate from, LocalDate to, String expectedCurrency, String expectedZone) {
        return !from.isBefore(coveredFrom) && !to.isAfter(coveredTo)
                && currency.equals(expectedCurrency) && reportZone.equals(expectedZone);
    }
}
