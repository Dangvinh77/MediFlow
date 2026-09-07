package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event tiêu thụ: "một đơn thuốc vừa được kê" (routing key {@code prescription.created},
 * do pharmacy-service phát). Đây là <b>cửa vào saga</b> kê đơn → hóa đơn → thanh toán → xuất
 * thuốc: billing tạo một hóa đơn ở trạng thái {@code AWAITING_PAYMENT}
 * (backend-spec/06-billing.md §3, §7).
 *
 * <p>Khai lại cùng tên/field với record pharmacy publish (06-billing.md §12.1) — contract qua
 * JSON, không import chéo service.
 *
 * @param eventId       khóa chống xử lý trùng — mỗi đơn chỉ tạo đúng một hóa đơn (BR-B6)
 * @param occurredAt    thời điểm kê đơn
 * @param correlationId mã truy vết xuyên suốt saga
 * @param prescriptionId đơn thuốc mở saga — cũng là {@code sourceRefId} của khoản phí DRUG
 * @param patientId     bệnh nhân
 * @param recordId      hồ sơ bệnh án của đơn (nếu có)
 * @param departmentId  khoa kê đơn — gắn vào khoản phí (BR-B8)
 * @param totalAmount   tổng tiền thuốc do pharmacy chốt tại thời điểm kê
 * @param items         các dòng thuốc trong đơn (để đối chiếu / hiển thị)
 */
public record PrescriptionCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        BigDecimal totalAmount,
        List<Item> items
) {
    /** Một dòng thuốc trong đơn — {@code price} là giá chụp tại thời điểm kê. */
    public record Item(UUID drugId, String drugName, int quantity, BigDecimal price) {}
}
