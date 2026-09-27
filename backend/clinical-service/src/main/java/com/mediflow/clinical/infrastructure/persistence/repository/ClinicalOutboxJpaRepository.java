package com.mediflow.clinical.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;

public interface ClinicalOutboxJpaRepository extends JpaRepository<ClinicalOutboxEventJpaEntity, UUID> {
    @Query(value = """
            SELECT * FROM clinical_outbox_event
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ClinicalOutboxEventJpaEntity> lockUnpublished(@Param("limit") int limit);
}
