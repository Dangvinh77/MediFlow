package com.mediflow.notification.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.notification.infrastructure.persistence.jpaEntity.NotificationJpaEntity;

/**
 * Spring Data repository cho bảng {@code NOTIFICATION} (backend-spec/07-notification.md §5, §13.3).
 */
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    /** Thông báo của một bệnh nhân, mới nhất trước — khớp index {@code idx_notification_patient}. */
    Page<NotificationJpaEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
