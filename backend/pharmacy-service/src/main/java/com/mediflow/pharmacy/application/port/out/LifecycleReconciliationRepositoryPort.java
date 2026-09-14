package com.mediflow.pharmacy.application.port.out;

import java.util.List;

import com.mediflow.pharmacy.application.dto.response.ReconciliationFinding;

/** Read-only query port for detecting lifecycle and reservation inconsistencies. */
public interface LifecycleReconciliationRepositoryPort {

    /**
     * Reads at most {@code limit} findings without changing any pharmacy data.
     *
     * @param limit maximum number of findings
     * @return deterministic findings ordered by prescription and anomaly type
     */
    List<ReconciliationFinding> findMismatches(int limit);
}
