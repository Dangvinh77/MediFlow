package com.mediflow.report.application.dto.response;

import java.util.UUID;

/** Internal progress; VERIFIED is finite-manifest equality, never a public financial read approval. */
public record CashReplayProgress(UUID generationId, Status status, long sourceReceipts, long appliedReceipts,
        int snapshotVersion, int projectorVersion) {
    public enum Status { BUILDING, VERIFIED, FAILED }

    public CashReplayProgress {
        if (generationId == null || status == null || sourceReceipts < 0 || appliedReceipts < 0
                || appliedReceipts > sourceReceipts || snapshotVersion < 1 || projectorVersion < 1
                || (status == Status.VERIFIED && sourceReceipts != appliedReceipts)) {
            throw new IllegalArgumentException("Invalid cash replay progress");
        }
    }
}
