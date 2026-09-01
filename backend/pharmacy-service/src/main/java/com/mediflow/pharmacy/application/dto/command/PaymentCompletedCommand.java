package com.mediflow.pharmacy.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lệnh application biểu diễn một lần thanh toán hóa đơn thuốc đã hoàn tất.
 *
 * <p>Command này tách tầng application khỏi RabbitMQ: consumer có thể nhận một payload
 * bên ngoài rồi chuyển thành command, còn application chỉ xử lý dữ liệu nghiệp vụ cần thiết.
 * {@code eventId} là khóa idempotency; {@code prescriptionId} xác định đơn cần xuất;
 * {@code invoiceId}, {@code patientId} và {@code correlationId} được giữ lại để phục vụ
 * truy vết và nhánh bù trừ saga.</p>
 *
 * @param eventId mã duy nhất của event, dùng để chống xử lý trùng
 * @param occurredAt thời điểm billing hoàn tất thanh toán
 * @param correlationId mã truy vết xuyên suốt saga
 * @param invoiceId hóa đơn đã được thanh toán
 * @param patientId bệnh nhân sở hữu hóa đơn
 * @param departmentId khoa phát sinh đơn thuốc
 * @param prescriptionId đơn thuốc cần xuất
 * @param totalAmount tổng số tiền đã thanh toán
 * @param paymentMethod phương thức thanh toán do billing công bố
 */
public record PaymentCompletedCommand(
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

    /**
     * Kiểm tra các trường bắt buộc ngay khi driving adapter tạo command.
     * Payload thiếu contract sẽ bị từ chối trước khi chạm vào tồn kho.
     */
    public PaymentCompletedCommand {
        Objects.requireNonNull(eventId, "eventId không được null");
        Objects.requireNonNull(occurredAt, "occurredAt không được null");
        Objects.requireNonNull(correlationId, "correlationId không được null");
        Objects.requireNonNull(invoiceId, "invoiceId không được null");
        Objects.requireNonNull(patientId, "patientId không được null");
        Objects.requireNonNull(departmentId, "departmentId không được null");
        Objects.requireNonNull(prescriptionId, "prescriptionId không được null");
        Objects.requireNonNull(totalAmount, "totalAmount không được null");
        Objects.requireNonNull(paymentMethod, "paymentMethod không được null");
    }
}
