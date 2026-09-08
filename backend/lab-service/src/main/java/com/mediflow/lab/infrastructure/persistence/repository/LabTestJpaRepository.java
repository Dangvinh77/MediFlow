package com.mediflow.lab.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabTestJpaEntity;

import jakarta.persistence.LockModeType;

public interface LabTestJpaRepository extends JpaRepository<LabTestJpaEntity, UUID> {
    @EntityGraph(attributePaths = "results")
    @Query("SELECT t FROM LabTestJpaEntity t WHERE t.testId = :id")
    Optional<LabTestJpaEntity> findAggregateById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM LabTestJpaEntity t WHERE t.testId = :id")
    Optional<LabTestJpaEntity> findByIdForUpdate(@Param("id") UUID id);
    @EntityGraph(attributePaths = "results")
    List<LabTestJpaEntity> findByPatientIdOrderByRequestedDateDesc(UUID patientId);
    @EntityGraph(attributePaths = "results")
    List<LabTestJpaEntity> findByRecordIdOrderByRequestedDateDesc(UUID recordId);
    @Query("""
            SELECT t FROM LabTestJpaEntity t
            WHERE (:departmentId IS NULL OR t.requestingDepartmentId = :departmentId)
              AND (:status IS NULL OR t.status = :status)
            """)
    Page<LabTestJpaEntity> search(@Param("departmentId") UUID departmentId,
                                  @Param("status") LabTestStatus status, Pageable pageable);
}
