package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.DischargeSummaryJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DischargeSummaryJpaRepository extends JpaRepository<DischargeSummaryJpaEntity, UUID> {
    Optional<DischargeSummaryJpaEntity> findByMaDotNoiTru(UUID admissionId);
}
