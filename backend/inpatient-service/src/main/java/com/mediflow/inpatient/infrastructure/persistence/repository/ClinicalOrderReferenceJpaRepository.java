package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.ClinicalOrderReferenceJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalOrderReferenceJpaRepository
        extends JpaRepository<ClinicalOrderReferenceJpaEntity, UUID> {
    Optional<ClinicalOrderReferenceJpaEntity> findByLoaiYLenhAndMaYLenhBenNgoai(
            ClinicalOrderType orderType, UUID externalOrderId);

    List<ClinicalOrderReferenceJpaEntity> findByMaDotNoiTruOrderByTaoLucAsc(UUID admissionId);
}
