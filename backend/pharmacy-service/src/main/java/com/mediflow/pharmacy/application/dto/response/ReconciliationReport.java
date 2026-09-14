package com.mediflow.pharmacy.application.dto.response;

import java.util.List;

/** Immutable result of a lifecycle reconciliation dry-run. */
/** Result returned by the lifecycle reconciliation use case.
 *
 * @param dryRun whether changes were suppressed
 * @param findings detected lifecycle anomalies
 */
public record ReconciliationReport(
        boolean dryRun,
        List<ReconciliationFinding> findings) {

    /** Always returns an immutable finding list to prevent accidental repair by callers. */
    public ReconciliationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** @return number of anomalies detected in this run */
    public int findingCount() {
        return findings.size();
    }
}
