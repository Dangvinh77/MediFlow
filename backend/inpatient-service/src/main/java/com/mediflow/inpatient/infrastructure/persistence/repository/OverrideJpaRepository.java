package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.OverrideJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideJpaRepository extends JpaRepository<OverrideJpaEntity, UUID> {
}
