package com.mediflow.notification.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;

/**
 * Event notification <b>phát</b>: "đã xử lý xong một thông báo" (routing key
 * {@code notification.sent}). Publish sau khi cập nhật trạng thái cuối cùng
 * ({@code SENT} hoặc {@code FAILED}) — backend-spec/07-notification.md §7 bước 7, §10, BR-N8.
 * Do out-port {@code NotificationEventPublisherPort} công bố; adapter RabbitMQ thật nằm ở
 * {@code infrastructure/messaging} (Phần 5/5).
 *
 * @param eventId        khóa để consumer dedupe
 * @param occurredAt     thời điểm chốt trạng thái
 * @param correlationId  mã truy vết xuyên suốt
 * @param notificationId bản ghi thông báo
 * @param patientId      bệnh nhân nhận
 * @param channel        kênh đã dùng: {@code EMAIL | SMS | IN_APP}
 * @param status         trạng thái cuối cùng: {@code SENT} hoặc {@code FAILED}
 */
public record NotificationSentEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID notificationId,
        UUID patientId,
        NotificationChannel channel,
        NotificationStatus status
) {}
