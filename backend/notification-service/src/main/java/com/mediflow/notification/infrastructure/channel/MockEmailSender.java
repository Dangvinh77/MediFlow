package com.mediflow.notification.infrastructure.channel;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mediflow.notification.application.port.out.NotificationSenderPort;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;

/**
 * Bộ gửi EMAIL mặc định — chỉ ghi log, luôn thành công (backend-spec/07-notification.md §12).
 * {@code spring-boot-starter-mail} đã có trong POM nhưng {@code JavaMailSender} chỉ tự cấu hình khi
 * khai {@code spring.mail.*}; service phải chạy được cả khi chưa có SMTP thật. Cắm
 * {@code JavaMailSender} thật vào sau chỉ cần thêm một adapter khác cho cùng
 * {@link NotificationSenderPort}, tầng application không đổi.
 *
 * <p><b>Không log {@code content}</b> ở mức INFO — có thể gợi ý chẩn đoán bệnh nhân
 * (backend-spec/07-notification.md §12).
 */
@Component
public class MockEmailSender implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public Optional<String> send(Notification n) {
        // Không log recipientAddress/content — PII (docs/ai/07-security-rbac.md).
        log.info("[MOCK EMAIL] Gửi thông báo notificationId={}", n.getNotificationId());
        return Optional.empty();
    }
}
