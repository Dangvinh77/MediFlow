package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SurgeryEventReceiptJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurgeryEventReceiptJpaRepository
        extends JpaRepository<SurgeryEventReceiptJpaEntity, UUID> {
    Optional<SurgeryEventReceiptJpaEntity> findByMaSuKien(UUID eventId);
    Optional<SurgeryEventReceiptJpaEntity> findByLoaiSuKienAndMaNghiepVu(
            String eventType, UUID operationId);
    List<SurgeryEventReceiptJpaEntity> findByMaCaMoOrderByNhanLucAsc(UUID surgeryCaseId);
}
