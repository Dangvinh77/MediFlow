package com.mediflow.lab.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabOutboxEventJpaEntity;

public interface LabOutboxEventJpaRepository extends JpaRepository<LabOutboxEventJpaEntity, UUID> {

    @Query(value = """
            SELECT * FROM lab_outbox_event
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<LabOutboxEventJpaEntity> lockNextBatch();

}
