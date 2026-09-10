package com.mediflow.notification.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity của bảng {@code PROCESSED_EVENT} — sổ chống xử lý trùng (BR-N5). Khi RabbitMQ gửi lại
 * cùng một message, {@code eventId} có mặt ở đây cho biết đã xử lý rồi để consumer bỏ qua.
 *
 * <p>{@code eventId} là khóa chính do message mang tới — <b>không</b> {@code @GeneratedValue}.
 */
@Entity
@Table(name = "PROCESSED_EVENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "routing_key", length = 100, nullable = false)
    private String routingKey;

    @CreationTimestamp
    @Column(name = "processed_at", updatable = false, nullable = false)
    private Instant processedAt;
}
