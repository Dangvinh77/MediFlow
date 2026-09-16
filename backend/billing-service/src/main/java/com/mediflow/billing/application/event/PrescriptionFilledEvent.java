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
 * <p>Contract v1 của Pharmacy không mang {@code dispenseId}. Billing vì vậy giữ
 * {@code Invoice.dispenseId} là {@code null} và không tự suy diễn một mã phiếu xuất.
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
