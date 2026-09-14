package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.response.ReconciliationReport;

/** Driving port for the safe, read-only pharmacy lifecycle reconciliation job. */
public interface ReconcilePharmacyUseCase {

    /**
     * Runs a dry-run reconciliation and never repairs rows automatically.
     *
     * @param limit maximum number of findings to report
     * @return immutable report suitable for operational logging
     */
    ReconciliationReport reconcileDryRun(int limit);
}
