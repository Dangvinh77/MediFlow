package com.mediflow.surgery.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Stable HTTP result for the local cancellation command; it is not the future event payload. */
public record CancelSurgeryResponse(
        UUID surgeryCaseId,
        long caseRevision,
        String status,
        UUID scheduleId,
        long scheduleRevision,
        Instant cancelledAt,
        boolean replayed) {
}
