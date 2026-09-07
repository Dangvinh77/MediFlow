package com.mediflow.notification.application.port.in;

import java.util.UUID;

/**
 * Record <b>nội bộ</b> do consumer dựng từ một event RabbitMQ, để {@link SendNotificationUseCase}
 * không bao giờ nhìn thấy kiểu của AMQP (backend-spec/07-notification.md §5).
 *
 * <p>Consumer ({@code NotificationEventConsumer}, Phần 5/5) nhận 6 routing key
 * ({@code patient.created}, {@code appointment.created}, {@code lab.result.created},
 * {@code prescription.filled}, {@code payment.completed}, {@code payment.failed}), dựng nội dung
 * từ {@code NotificationTemplates} (§8) rồi gói vào trigger này. {@code email}/{@code phone} có
 * thể null — use case chọn kênh theo thứ tự ưu tiên EMAIL → SMS → IN_APP (BR-N9).
 *
 * @param eventId    khóa chống xử lý trùng (BR-N5)
 * @param routingKey routing key gốc, để ghi vào sổ {@code PROCESSED_EVENT}
 * @param patientId  bệnh nhân nhận thông báo
 * @param title      tiêu đề đã dựng từ template
 * @param content    nội dung đã dựng từ template
 * @param email      email bệnh nhân nếu biết (null nếu không)
 * @param phone      số điện thoại bệnh nhân nếu biết (null nếu không)
 */
public record NotificationTrigger(
        UUID eventId,
        String routingKey,
        UUID patientId,
        String title,
        String content,
        String email,
        String phone
) {}
