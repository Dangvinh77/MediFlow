package com.mediflow.lab.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.lab.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
    @Modifying
    @Query(value = "INSERT INTO processed_event(event_id, routing_key) VALUES (:eventId, :routingKey)",
            nativeQuery = true)
    void insertEvent(@Param("eventId") UUID eventId, @Param("routingKey") String routingKey);
}
