package com.mediflow.report.application.dto.response;

import java.time.Instant;
import java.util.Objects;

import com.mediflow.report.domain.exception.ReportRuleException;

/** Aggregate diagnostic state only; no source IDs, money, payloads or publication authority. */
public record ReportTelemetrySnapshot(Instant sampledAt, long pendingAdmissions,
        Instant oldestPendingObservedAt, long legacyUnverifiedSources, Replay operational, Replay cash) {
    public ReportTelemetrySnapshot {
        Objects.requireNonNull(sampledAt);
        Objects.requireNonNull(operational);
        Objects.requireNonNull(cash);
        require(pendingAdmissions >= 0 && legacyUnverifiedSources >= 0);
        require((pendingAdmissions == 0) == (oldestPendingObservedAt == null));
    }

    /** Progress totals refer only to BUILDING generations, never live source coverage. */
    public record Replay(long building, long verified, long failed, long sourceInputs,
            long appliedInputs, Instant oldestBuildingAt) {
        public Replay {
            require(building >= 0 && verified >= 0 && failed >= 0);
            require(sourceInputs >= 0 && appliedInputs >= 0 && appliedInputs <= sourceInputs);
            require((building == 0) == (oldestBuildingAt == null));
            require(building != 0 || sourceInputs == 0);
        }
        public long remainingInputs() { return sourceInputs - appliedInputs; }
    }

    private static void require(boolean valid) {
        if (!valid) throw new ReportRuleException("REPORT_TELEMETRY_INVALID", "Invalid aggregate telemetry state");
    }
}
