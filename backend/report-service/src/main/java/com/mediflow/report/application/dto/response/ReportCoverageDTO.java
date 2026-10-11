package com.mediflow.report.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportCoverageDTO(LocalDate requestedFrom, LocalDate requestedTo, Status status,
        LocalDate coveredFrom, LocalDate coveredTo, UUID generationId, Instant sourceObservedAt, boolean snapshotOnly) {
    public enum Status { AVAILABLE_SNAPSHOT, UNAVAILABLE_HISTORY, UNAVAILABLE_PUBLICATION }
}
