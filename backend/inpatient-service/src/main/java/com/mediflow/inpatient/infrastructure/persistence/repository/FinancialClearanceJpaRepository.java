package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.FinancialClearanceJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialClearanceJpaRepository extends JpaRepository<FinancialClearanceJpaEntity, UUID> {
}
