package com.mediflow.clinical.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;

public interface ClinicalOutboxJpaRepository extends JpaRepository<ClinicalOutboxEventJpaEntity, UUID> {
    String DEFERRED_OUTPATIENT_FACT_PREDICATE = """
            (
                event_type = 'admission.requested'
                OR (
                    event_type = 'medicalrecord.completed'
                    AND upper(coalesce(payload ->> 'disposition', payload #>> '{payload,disposition}', ''))
                        IN ('ADMISSION', 'TRANSFER')
                )
                OR upper(coalesce(
                    payload ->> 'careEpisodeType',
                    payload #>> '{payload,careEpisodeType}',
                    '')) = 'ADMISSION'
            )
            """;

    @Query(value = """
            SELECT * FROM clinical_outbox_event
            WHERE published_at IS NULL
              AND held_at IS NULL
              AND quarantined_at IS NULL
            ORDER BY occurred_at, event_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ClinicalOutboxEventJpaEntity> lockUnpublished(@Param("limit") int limit);

    @Query(value = """
            SELECT * FROM clinical_outbox_event
            WHERE published_at IS NULL
              AND held_at IS NULL
              AND quarantined_at IS NULL
              AND NOT """ + DEFERRED_OUTPATIENT_FACT_PREDICATE + """
            ORDER BY occurred_at, event_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ClinicalOutboxEventJpaEntity> lockUnpublishedForOutpatientMvp(@Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE clinical_outbox_event
            SET held_at = :heldAt, hold_reason = :reason
            WHERE published_at IS NULL
              AND held_at IS NULL
              AND quarantined_at IS NULL
              AND """ + DEFERRED_OUTPATIENT_FACT_PREDICATE, nativeQuery = true)
    int holdDeferredPendingEvents(@Param("heldAt") Instant heldAt, @Param("reason") String reason);
}
