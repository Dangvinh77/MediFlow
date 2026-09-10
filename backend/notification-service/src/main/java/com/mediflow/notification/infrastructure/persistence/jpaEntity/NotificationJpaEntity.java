package com.mediflow.notification.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity của bảng {@code NOTIFICATION} — một bản ghi kết quả gửi thông báo. Mọi bất biến
 * (BR-N1/N2 địa chỉ hợp lệ, BR-N7 không gửi lại) sống trong {@code domain/model/Notification};
 * entity chỉ phản ánh lược đồ {@code V1__init.sql}.
 *
 * <p>Không có {@code updated_at}: thông báo chỉ chuyển {@code PENDING → SENT|FAILED} một lần rồi
 * kết thúc, {@code sent_at} đã ghi mốc đó.
 */
@Entity
@Table(name = "NOTIFICATION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id", updatable = false, nullable = false)
    private UUID notificationId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10, nullable = false)
    private NotificationChannel channel;

    @Column(name = "recipient_address", length = 150)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
