package com.mediflow.pharmacy.application.dto.response;

import java.util.UUID;

/** One lifecycle/reservation anomaly found by the dry-run reconciliation query. */
public record ReconciliationFinding(UUID prescriptionId, String anomalyType, String details) {

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
