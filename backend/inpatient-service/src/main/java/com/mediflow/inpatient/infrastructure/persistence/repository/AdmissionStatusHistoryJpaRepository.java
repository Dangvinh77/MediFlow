package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionStatusHistoryJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionStatusHistoryJpaRepository
        extends JpaRepository<AdmissionStatusHistoryJpaEntity, UUID> {
}
