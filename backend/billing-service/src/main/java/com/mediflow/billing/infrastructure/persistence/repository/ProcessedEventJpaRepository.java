package com.mediflow.billing.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;

/** Spring Data repository cho bảng {@code PROCESSED_EVENT} — sổ chống xử lý trùng (BR-B6/BR-B7). */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
}
