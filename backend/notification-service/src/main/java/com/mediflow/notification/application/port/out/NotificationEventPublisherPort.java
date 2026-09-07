package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.event.NotificationSentEvent;

/**
 * Out-port — "tôi cần ai đó biết cách báo lại kết quả gửi cho hệ thống".
 * Application phải công bố {@code notification.sent} nhưng không được đụng RabbitMQ. Adapter
 * thật là {@code NotificationEventPublisherAdapter} trong {@code infrastructure/messaging}
 * (Phần 5/5), publish sau khi transaction commit (backend-spec/07-notification.md §10,
 * docs/ai/06-events-rabbitmq.md).
 */
public interface NotificationEventPublisherPort {

    /** Publish {@code notification.sent} kèm trạng thái cuối cùng ({@code SENT}/{@code FAILED}) — BR-N8. */
    void publishSent(NotificationSentEvent e);
}
