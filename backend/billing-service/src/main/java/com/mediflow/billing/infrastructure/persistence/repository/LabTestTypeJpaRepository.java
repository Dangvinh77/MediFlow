package com.mediflow.billing.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.LabTestTypeJpaEntity;

/** Spring Data repository cho bảng chiếu local {@code LAB_TEST_TYPE} ({@code labId -> labType}). */
public interface LabTestTypeJpaRepository extends JpaRepository<LabTestTypeJpaEntity, UUID> {
}
