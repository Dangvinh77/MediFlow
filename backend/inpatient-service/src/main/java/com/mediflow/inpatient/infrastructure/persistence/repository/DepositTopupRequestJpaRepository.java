package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.DepositTopupRequestJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositTopupRequestJpaRepository extends JpaRepository<DepositTopupRequestJpaEntity, UUID> {
}
