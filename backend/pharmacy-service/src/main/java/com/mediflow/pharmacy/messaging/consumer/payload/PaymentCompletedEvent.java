package com.mediflow.pharmacy.messaging.consumer.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload nhận từ routing key {@code payment.completed} do billing-service phát.
 *
 * <p>Đây là DTO tại biên RabbitMQ, không phải domain model. Consumer chuyển payload này
 * thành application command trước khi gọi in-port. {@code paymentMethod} dùng kiểu
 * {@link String} để pharmacy không phụ thuộc enum thuộc bounded context billing.</p>
 *
 * @param eventId mã duy nhất của event
 * @param occurredAt thời điểm event được tạo
 * @param correlationId mã truy vết xuyên suốt saga
 * @param invoiceId hóa đơn đã được thanh toán
 * @param patientId bệnh nhân sở hữu hóa đơn
 * @param departmentId khoa phát sinh đơn thuốc
 * @param prescriptionId đơn thuốc pharmacy phải xuất
 * @param totalAmount tổng số tiền đã thanh toán
 * @param paymentMethod phương thức thanh toán, được serialize dưới dạng chuỗi
 */
public record PaymentCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID patientId,
        UUID departmentId,
        UUID prescriptionId,
        BigDecimal totalAmount,
        String paymentMethod
) {
}
