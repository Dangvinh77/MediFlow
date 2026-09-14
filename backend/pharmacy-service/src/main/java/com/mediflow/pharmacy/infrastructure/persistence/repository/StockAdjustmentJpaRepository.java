package com.mediflow.pharmacy.infrastructure.persistence.repository;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.StockAdjustmentJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for stock adjustment audit rows. */
public interface StockAdjustmentJpaRepository extends JpaRepository<StockAdjustmentJpaEntity, UUID> {
}
