package com.mediflow.clinical.infrastructure.persistence.repository;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalLabOrderJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalLabOrderJpaRepository extends JpaRepository<ClinicalLabOrderJpaEntity, UUID> {
    List<ClinicalLabOrderJpaEntity> findByRecordIdOrderByOrderedAtAscSourceOrderIdAsc(UUID recordId);
}
