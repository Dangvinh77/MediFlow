package com.mediflow.notification.infrastructure.channel;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mediflow.notification.application.port.out.NotificationSenderPort;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;

/**
 * Bộ gửi SMS mặc định — chưa có nhà cung cấp SMS thật nào được tích hợp, chỉ ghi log và luôn
 * thành công (backend-spec/07-notification.md §12). Đừng bịa ra một tích hợp không tồn tại; cắm
 * nhà cung cấp thật vào sau chỉ cần một adapter khác cho cùng {@link NotificationSenderPort}.
 */
@Component
public class MockSmsSender implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public Optional<String> send(Notification n) {
        // Không log recipientAddress/content — PII (docs/ai/07-security-rbac.md).
        log.info("[MOCK SMS] Gửi thông báo notificationId={}", n.getNotificationId());
        return Optional.empty();
    }
}
