package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.domain.model.enums.BedAssignmentStatus;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.BedAssignmentJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BedAssignmentJpaRepository extends JpaRepository<BedAssignmentJpaEntity, UUID> {
    Optional<BedAssignmentJpaEntity> findFirstByMaDotNoiTruAndTrangThai(
            UUID admissionId, BedAssignmentStatus status);

    Optional<BedAssignmentJpaEntity> findFirstByMaGiuongAndTrangThai(
            UUID bedId, BedAssignmentStatus status);
}
