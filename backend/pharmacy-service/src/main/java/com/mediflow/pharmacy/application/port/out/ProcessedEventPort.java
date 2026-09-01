package com.mediflow.pharmacy.application.port.out;

import java.util.UUID;

/**
 * Out-port — "tôi cần ai đó biết cách ghi và tra sổ chống xử lý trùng".
 * Vì sao cần: RabbitMQ có thể gửi lại cùng một event. Nếu không kiểm tra, một tin
 * payment.completed đến hai lần sẽ xuất thuốc hai lần (BR-D9). Application không được
 * tự đụng DB; {@code ProcessedEventPersistenceAdapter} (trong infrastructure) sẽ hiện thực,
 * lưu vào bảng PROCESSED_EVENT.
 */
public interface ProcessedEventPort {

    /** Kiểm tra event (theo eventId) đã được xử lý chưa. Đã xử lý rồi thì consumer phải bỏ qua (BR-D9). */
    boolean alreadyProcessed(UUID eventId);

    /** Đánh dấu event đã được xử lý — gọi trong cùng transaction với nghiệp vụ để chống xử lý trùng (BR-D9). */
    void markProcessed(UUID eventId, String routingKey);
}
