package com.mediflow.notification.infrastructure.channel;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.mediflow.notification.application.port.out.NotificationSenderPort;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;

/**
 * Bộ gửi IN_APP — không gọi ra ngoài, đã lưu vào {@code NOTIFICATION} là đủ để bệnh nhân đọc qua
 * {@code GET /api/v1/notifications/...} (backend-spec/07-notification.md §12). Luôn thành công.
 */
@Component
public class InAppSender implements NotificationSenderPort {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public Optional<String> send(Notification n) {
        return Optional.empty();
    }
}
