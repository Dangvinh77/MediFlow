package com.mediflow.pharmacy.infrastructure.persistence.repository;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DispenseSlipJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DispenseSlipJpaRepository extends JpaRepository<DispenseSlipJpaEntity, UUID> {
    Optional<DispenseSlipJpaEntity> findByPrescriptionId(UUID prescriptionId);
}