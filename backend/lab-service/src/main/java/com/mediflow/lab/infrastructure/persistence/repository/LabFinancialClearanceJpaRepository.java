package com.mediflow.lab.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabFinancialClearanceJpaEntity;

public interface LabFinancialClearanceJpaRepository
        extends JpaRepository<LabFinancialClearanceJpaEntity, UUID> {

    @Query("""
            SELECT c FROM LabFinancialClearanceJpaEntity c
            WHERE c.test.testId = :testId AND (c.expiresAt IS NULL OR c.expiresAt > :at)
            ORDER BY c.grantedAt DESC
            """)
    List<LabFinancialClearanceJpaEntity> findValidByTestId(
            @Param("testId") UUID testId, @Param("at") Instant at);

    List<LabFinancialClearanceJpaEntity> findByTestTestIdOrderByGrantedAtDesc(UUID testId);
}
