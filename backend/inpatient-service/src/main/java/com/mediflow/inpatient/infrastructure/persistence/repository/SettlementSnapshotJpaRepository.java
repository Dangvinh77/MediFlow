package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SettlementSnapshotJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementSnapshotJpaRepository extends JpaRepository<SettlementSnapshotJpaEntity, UUID> {
    List<SettlementSnapshotJpaEntity> findByMaDotNoiTruOrderByHoanTatLucDescMaQuyetToanDesc(UUID admissionId);
}
