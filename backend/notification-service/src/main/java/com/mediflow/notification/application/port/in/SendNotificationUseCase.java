package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.request.SendNotificationRequest;
import com.mediflow.notification.application.dto.response.NotificationDTO;

/**
 * In-port — "gửi một thông báo" (backend-spec/07-notification.md §5).
 * {@code NotificationApplicationService} (Phần 3/5) hiện thực theo luồng 7 bước ở §7.
 * Cả hai lối vào (API và event) đều: lưu {@code PENDING} trước khi gửi (BR-N3), gửi thất bại
 * thì ghi {@code FAILED} + lý do chứ không ném lỗi (BR-N4), rồi publish {@code notification.sent}
 * kèm trạng thái cuối (BR-N8).
 */
public interface SendNotificationUseCase {

    /**
     * Gửi theo yêu cầu trực tiếp qua API ({@code POST /api/v1/notifications/send}, role ADMIN/SYSTEM).
     * Địa chỉ không hợp lệ theo kênh → {@code NOTIFICATION_ADDRESS_INVALID} (422, BR-N1/BR-N2).
     */
    NotificationDTO send(SendNotificationRequest r);

    /**
     * Xử lý một thông báo bắt nguồn từ event nghiệp vụ. Do consumer gọi, đã gói dữ liệu vào
     * {@link NotificationTrigger} nên use case không phụ thuộc AMQP. Idempotent theo
     * {@code eventId} (BR-N5); chọn kênh theo thứ tự ưu tiên EMAIL → SMS → IN_APP (BR-N9).
     */
    void handleEvent(NotificationTrigger trigger);
}
