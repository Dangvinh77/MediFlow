package com.mediflow.billing.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;

/** Spring Data repository cho bảng {@code PROCESSED_EVENT} — sổ chống xử lý trùng (BR-B6/BR-B7). */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
    /** Insert-only marker: duplicate delivery must roll back, never merge an existing marker. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO processed_event(event_id, routing_key) VALUES (:eventId, :routingKey)", nativeQuery = true)
    void insertEvent(@org.springframework.data.repository.query.Param("eventId") UUID eventId,
                     @org.springframework.data.repository.query.Param("routingKey") String routingKey);
}
