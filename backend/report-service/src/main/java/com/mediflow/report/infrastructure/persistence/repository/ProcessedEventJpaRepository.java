package com.mediflow.report.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.report.infrastructure.persistence.ProcessedEventJpaEntity;

/** Atomic insert repository for event idempotency claims. */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {

    @Modifying
    @Query(value = "INSERT INTO PROCESSED_EVENT (event_id, routing_key) VALUES (:eventId, :routingKey) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("routingKey") String routingKey);
}
