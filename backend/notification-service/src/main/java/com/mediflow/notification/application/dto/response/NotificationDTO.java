package com.mediflow.notification.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;

/**
 * DTO một thông báo trả về cho client (backend-spec/07-notification.md §6).
 *
 * <p><b>Cố ý KHÔNG có {@code recipientAddress} và {@code retryCount}</b> — chúng là PII / dữ liệu
 * nội bộ, không bao giờ lộ ra ngoài. {@code Notification} (domain) không đi thẳng ra HTTP.
 *
 * @param notificationId mã thông báo
 * @param patientId      bệnh nhân nhận
 * @param title          tiêu đề
 * @param content        nội dung
 * @param channel        kênh đã dùng
 * @param status         {@code PENDING | SENT | FAILED}
 * @param failureReason  lý do thất bại (null nếu không thất bại)
 * @param createdAt      thời điểm tạo bản ghi
 * @param sentAt         thời điểm gửi thành công (null nếu chưa/không gửi được)
 */
public record NotificationDTO(
        UUID notificationId,
        UUID patientId,
        String title,
        String content,
        NotificationChannel channel,
        NotificationStatus status,
        String failureReason,
        Instant createdAt,
        Instant sentAt
) {}
