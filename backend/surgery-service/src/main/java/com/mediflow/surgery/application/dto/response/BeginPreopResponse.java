package com.mediflow.surgery.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Stable response for starting the pre-operative phase. */
public record BeginPreopResponse(
        UUID surgeryCaseId,
        long caseRevision,
        String status,
        Instant startedAt,
        boolean replayed) {
}
