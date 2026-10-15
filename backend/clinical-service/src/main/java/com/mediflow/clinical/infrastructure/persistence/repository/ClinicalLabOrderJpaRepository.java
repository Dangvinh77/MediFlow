package com.mediflow.clinical.infrastructure.persistence.repository;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalLabOrderJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalLabOrderJpaRepository extends JpaRepository<ClinicalLabOrderJpaEntity, UUID> {}
