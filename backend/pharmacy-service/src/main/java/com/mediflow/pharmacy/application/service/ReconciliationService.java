package com.mediflow.pharmacy.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.mediflow.pharmacy.application.port.in.ReconcilePharmacyUseCase;
import com.mediflow.pharmacy.application.port.out.LifecycleReconciliationRepositoryPort;
import com.mediflow.pharmacy.application.dto.response.ReconciliationReport;

/**
 * Runs the lifecycle reconciliation in read-only mode.
 *
 * <p>Unknown or legacy rows are reported with their prescription id and anomaly type. This
 * service intentionally has no mutation port: an operator must review and repair an anomaly
 * through a separately designed migration or use case.</p>
 */
@Service
public class ReconciliationService implements ReconcilePharmacyUseCase {

    private final LifecycleReconciliationRepositoryPort repository;

    /** Creates the read-only reconciliation service. */
    public ReconciliationService(LifecycleReconciliationRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** {@inheritDoc} */
    @Override
    public ReconciliationReport reconcileDryRun(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Reconciliation limit must be positive");
        }
        return new ReconciliationReport(true, repository.findMismatches(limit));
    }
}
