package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.TreatmentEntryJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentEntryJpaRepository extends JpaRepository<TreatmentEntryJpaEntity, UUID> {
    Optional<TreatmentEntryJpaEntity> findByMaDotNoiTruAndMaMucDienBien(UUID admissionId, UUID entryId);
}
