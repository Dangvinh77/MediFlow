package com.mediflow.pharmacy.application.dto.response;

import java.util.UUID;

/** A single lifecycle inconsistency found by reconciliation.
 *
 * @param prescriptionId prescription identifier
 * @param anomalyType stable anomaly code
 * @param details diagnostic details
 */
public record ReconciliationFinding(
        UUID prescriptionId,
        String anomalyType,
        String details) {

    /** Creates a validated finding that can be safely logged without payload or PII. */
    public ReconciliationFinding {
        if (prescriptionId == null) {
            throw new IllegalArgumentException("prescriptionId is required");
        }
        if (anomalyType == null || anomalyType.isBlank()) {
            throw new IllegalArgumentException("anomalyType is required");
        }
        details = details == null ? "" : details;
    }
}
