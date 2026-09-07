package com.mediflow.billing.application.port.out;

import java.util.UUID;

/**
 * Out-port — "tôi cần ai đó biết cách ghi và tra sổ chống xử lý trùng".
 * RabbitMQ có thể gửi lại cùng một event; nếu không kiểm tra, một tin
 * {@code prescription.created} đến hai lần sẽ tạo hai hóa đơn (BR-B6). Hiện thực là
 * {@code ProcessedEventPersistenceAdapter} (infrastructure, Phần 4/5), lưu vào bảng
 * {@code PROCESSED_EVENT}.
 */
public interface ProcessedEventPort {

    /** Event (theo {@code eventId}) đã xử lý chưa. Đã rồi thì consumer bỏ qua. */
    boolean alreadyProcessed(UUID eventId);

    /** Đánh dấu event đã xử lý — gọi trong cùng transaction với nghiệp vụ để chống xử lý trùng. */
    void markProcessed(UUID eventId, String routingKey);
}
