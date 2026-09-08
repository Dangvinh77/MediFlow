package com.mediflow.notification.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.notification.application.port.out.NotificationRepositoryPort;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.infrastructure.persistence.jpaEntity.NotificationJpaEntity;
import com.mediflow.notification.infrastructure.persistence.repository.NotificationJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link NotificationRepositoryPort} — đóng gói Spring Data JPA. Đổi
 * {@link PageQuery}/{@link PageResult} của common sang/từ {@code Pageable}/{@code Page} tại đây;
 * tầng application không bao giờ thấy kiểu Spring Data. Map entity ↔ domain thủ công.
 *
 * <p>Phải lưu được cả bản ghi {@code PENDING} (bước 4 §7) lẫn bản ghi đã chuyển {@code SENT}/
 * {@code FAILED} (BR-N3/BR-N4) — {@code save} một domain đã có id sẽ cập nhật, giữ nguyên
 * {@code created_at}.
 */
@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationRepositoryPort {

    private final NotificationJpaRepository jpaRepo;

    @Override
    public Notification save(Notification n) {
        // Flush để id + created_at do Hibernate sinh có mặt trong domain trả về.
        return toDomain(jpaRepo.saveAndFlush(toEntity(n)));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Notification> findByPatient(UUID patientId, PageQuery page) {
        Page<NotificationJpaEntity> found = jpaRepo.findByPatientIdOrderByCreatedAtDesc(
                patientId, PageRequest.of(page.page(), page.size()));
        return PageResult.of(
                found.getContent().stream().map(this::toDomain).toList(),
                found.getTotalElements(), page.page(), page.size());
    }

    // ---- map entity ↔ domain (thủ công, không MapStruct) ----

    private Notification toDomain(NotificationJpaEntity e) {
        return Notification.restore(
                e.getNotificationId(), e.getPatientId(), e.getTitle(), e.getContent(), e.getChannel(),
                e.getRecipientAddress(), e.getStatus(), e.getFailureReason(), e.getRetryCount(),
                e.getCreatedAt(), e.getSentAt());
    }

    private NotificationJpaEntity toEntity(Notification n) {
        return NotificationJpaEntity.builder()
                .notificationId(n.getNotificationId())
                .patientId(n.getPatientId())
                .title(n.getTitle())
                .content(n.getContent())
                .channel(n.getChannel())
                .recipientAddress(n.getRecipientAddress())
                .status(n.getStatus())
                .failureReason(n.getFailureReason())
                .retryCount(n.getRetryCount())
                // Lưu mới: Hibernate gán created_at; cập nhật: giữ nguyên của bản ghi cũ.
                .createdAt(n.getCreatedAt())
                .sentAt(n.getSentAt())
                .build();
    }
}
