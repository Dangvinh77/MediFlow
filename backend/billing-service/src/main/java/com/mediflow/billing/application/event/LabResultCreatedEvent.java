package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ: "một kết quả xét nghiệm vừa có" (routing key {@code lab.result.created},
 * do lab-service phát). Billing lắng nghe để sinh một khoản phí {@code LAB}, số tiền lấy từ
 * {@code PriceListPort.labFee(labType)} (backend-spec/06-billing.md §7).
 *
 * <p>Contract qua JSON — billing tự khai lại record này (06-billing.md §12.1).
 *
 * @param eventId      khóa chống xử lý trùng (BR-B7)
 * @param occurredAt   thời điểm lab trả kết quả
 * @param correlationId mã truy vết xuyên suốt
 * @param labId        phiếu/kết quả xét nghiệm — dùng làm {@code sourceRefId} của khoản phí LAB
 * @param labType      loại xét nghiệm — tra bảng giá {@code labFee(labType)}
 * @param recordId     hồ sơ bệnh án liên quan (nếu có)
 * @param patientId    bệnh nhân
 * @param departmentId khoa chỉ định xét nghiệm — gắn vào khoản phí (BR-B8)
 */
public record LabResultCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        String labType,
        UUID recordId,
        UUID patientId,
        UUID departmentId
) {}
