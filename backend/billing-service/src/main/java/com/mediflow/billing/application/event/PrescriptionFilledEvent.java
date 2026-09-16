package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event tiêu thụ: "đơn thuốc đã xuất thành công" (routing key {@code prescription.filled},
 * do pharmacy-service phát). Nhánh <b>saga thành công</b>: billing chuyển hóa đơn tương ứng
 * sang {@code COMPLETED} (backend-spec/06-billing.md §3, §7 "onPrescriptionFilled", BR-B11).
 *
 * <p>Khai lại cùng tên/field với record pharmacy publish (06-billing.md §12.1).
 *
 * <p><b>Quyết định cross-team (2026-09-16):</b> §7 nói billing "đặt {@code dispenseId}" khi hoàn
 * tất saga, nhưng payload pharmacy không mang {@code dispenseId}. Đã chốt với pharmacy: billing
 * bỏ hẳn việc gán {@code dispenseId} (không đợi bổ sung field) — khai record đúng payload thật
 * đang có, không suy diễn thêm trường.
 *
 * @param eventId        khóa chống xử lý trùng
 * @param occurredAt     thời điểm xuất thuốc xong
 * @param correlationId  mã truy vết xuyên suốt saga
 * @param prescriptionId đơn thuốc — billing tra hóa đơn theo trường này
 * @param patientId      bệnh nhân
 * @param departmentId   khoa kê đơn
 * @param totalAmount    tổng tiền thuốc đã xuất
 * @param dispensedItems các dòng thuốc thực tế đã xuất khỏi kho
 */
public record PrescriptionFilledEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId,
        UUID departmentId,
        BigDecimal totalAmount,
        List<DispensedItem> dispensedItems
) {
    /** Một dòng thuốc đã thực sự xuất ra khỏi kho. */
    public record DispensedItem(UUID drugId, String drugName, int quantity) {}
}
