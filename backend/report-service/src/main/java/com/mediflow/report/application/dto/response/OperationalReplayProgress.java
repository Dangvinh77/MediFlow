package com.mediflow.report.application.dto.response;

import java.util.UUID;

/** VERIFIED means reconciled against this finite manifest, not eligible for live read cutover. */
public record OperationalReplayProgress(UUID generationId, Status status, long sourceEvents, long appliedEvents) {
    public enum Status { BUILDING, VERIFIED, FAILED }

    public OperationalReplayProgress {
        if (generationId == null || status == null || sourceEvents < 0 || appliedEvents < 0
                || appliedEvents > sourceEvents) {
            throw new IllegalArgumentException("Invalid replay progress");
        }
    }
}
