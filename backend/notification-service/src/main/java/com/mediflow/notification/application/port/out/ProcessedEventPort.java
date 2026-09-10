package com.mediflow.notification.application.port.out;

import java.util.UUID;

/**
 * Out-port — "tôi cần ai đó biết cách ghi và tra sổ chống xử lý trùng".
 * RabbitMQ có thể gửi lại cùng một event; không kiểm tra thì một tin đến hai lần sẽ tạo hai
 * thông báo (BR-N5). Hiện thực là {@code ProcessedEventPersistenceAdapter} (infrastructure,
 * Phần 4/5), lưu vào bảng {@code PROCESSED_EVENT}. Việc đánh dấu phải nằm <b>trong cùng
 * transaction</b> với bước lưu thông báo {@code PENDING} (backend-spec/07-notification.md §7 bước 6).
 */
public interface ProcessedEventPort {

    /** Event (theo {@code eventId}) đã xử lý chưa. Đã rồi thì consumer return ngay. */
    boolean alreadyProcessed(UUID eventId);

    /** Đánh dấu event đã xử lý — gọi trong cùng transaction với nghiệp vụ. */
    void markProcessed(UUID eventId, String routingKey);
}
