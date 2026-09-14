package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import com.mediflow.pharmacy.application.port.out.StockAdjustmentRepositoryPort;
import com.mediflow.pharmacy.domain.model.StockAdjustment;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.StockAdjustmentJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockAdjustmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persistence adapter mapping stock audit domain records to JPA rows. */
@Component
@RequiredArgsConstructor
public class StockAdjustmentPersistenceAdapter implements StockAdjustmentRepositoryPort {

    private final StockAdjustmentJpaRepository repository;

    /** {@inheritDoc} */
    @Override
    public StockAdjustment save(StockAdjustment adjustment) {
        repository.saveAndFlush(StockAdjustmentJpaEntity.builder()
                .adjustmentId(adjustment.getAdjustmentId())
                .drugId(adjustment.getDrugId())
                .beforeStock(adjustment.getBeforeStock())
                .delta(adjustment.getDelta())
                .afterStock(adjustment.getAfterStock())
                .reason(adjustment.getReason())
                .actorId(adjustment.getActorId())
                .correlationId(adjustment.getCorrelationId())
                .occurredAt(adjustment.getOccurredAt())
                .build());
        return adjustment;
    }
}
