package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.out.LifecycleReconciliationRepositoryPort;
import com.mediflow.pharmacy.application.dto.response.ReconciliationFinding;
import com.mediflow.pharmacy.infrastructure.persistence.repository.LifecycleMismatchProjection;
import com.mediflow.pharmacy.infrastructure.persistence.repository.LifecycleReconciliationJpaRepository;

import lombok.RequiredArgsConstructor;

/** Read-only adapter for lifecycle/reservation reconciliation. */
@Component
@RequiredArgsConstructor
public class LifecycleReconciliationPersistenceAdapter implements LifecycleReconciliationRepositoryPort {

    private final LifecycleReconciliationJpaRepository repository;

    /** {@inheritDoc} */
    @Override
    public List<ReconciliationFinding> findMismatches(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Reconciliation limit must be positive");
        }
        return repository.findMismatches(PageRequest.of(0, limit)).stream()
                .map(this::toFinding)
                .toList();
    }

    private ReconciliationFinding toFinding(LifecycleMismatchProjection projection) {
        return new ReconciliationFinding(
                projection.getPrescriptionId(), projection.getAnomalyType(), projection.getDetails());
    }
}
