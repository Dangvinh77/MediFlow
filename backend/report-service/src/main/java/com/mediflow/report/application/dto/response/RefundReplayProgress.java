package com.mediflow.report.application.dto.response;

import java.util.UUID;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;

/** Finite accepted cash evidence only, not coverage approval or a financial publication. */
public record RefundReplayProgress(UUID generationId, Status status, long sourceRefunds,
        long processedRefunds, long pendingRefunds, long rejectedRefunds, int formatVersion) {
    public RefundReplayProgress {
        if (generationId == null || status == null || sourceRefunds < 0 || processedRefunds < 0
                || processedRefunds > sourceRefunds || pendingRefunds < 0 || rejectedRefunds < 0
                || pendingRefunds + rejectedRefunds > sourceRefunds || formatVersion < 1
                || (status == Status.VERIFIED && processedRefunds != sourceRefunds)) {
            throw new IllegalArgumentException("Invalid refund replay progress");
        }
    }
}
