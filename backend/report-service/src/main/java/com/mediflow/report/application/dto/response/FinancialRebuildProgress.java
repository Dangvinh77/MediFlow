package com.mediflow.report.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Internal finite-source progress. VERIFIED is local equality, never source acceptance/publication. */
public record FinancialRebuildProgress(UUID generationId, Status status, LocalDate coveredFrom, LocalDate coveredTo,
        String reportZone, Instant sourceObservedAt, int sourceInputs, int unresolvedInputs, int processedDays,
        String manifestFingerprint, String reasonCode) {
    public enum Status { BUILDING, VERIFIED, FAILED }
}
