package com.mediflow.pharmacy.infrastructure.persistence.repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.ProcessedEventJpaEntity;
import java.util.UUID;
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {

    /** Claim idempotency key atomically without raising a duplicate-key transaction error. */
    @Modifying
    @Query(value = "INSERT INTO PROCESSED_EVENT (event_id, routing_key) VALUES (:eventId, :routingKey) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("routingKey") String routingKey);
}
