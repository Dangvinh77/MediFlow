package com.mediflow.notification.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.notification.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;

/** Spring Data repository cho bảng {@code PROCESSED_EVENT} — sổ chống xử lý trùng (BR-N5). */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
}
