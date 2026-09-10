package com.mediflow.clinical.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.AttachedResultId;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.AttachedResultJpaEntity;

public interface AttachedResultJpaRepository
        extends JpaRepository<AttachedResultJpaEntity, AttachedResultId> {

    @Modifying
    @Query(value = """
            INSERT INTO attached_result(record_id, type, reference_id, summary)
            VALUES (:recordId, :type, :referenceId, :summary)
            ON CONFLICT (record_id, type, reference_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("recordId") UUID recordId,
                       @Param("type") String type,
                       @Param("referenceId") UUID referenceId,
                       @Param("summary") String summary);
}
