package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ: "một kết quả xét nghiệm vừa có" (routing key {@code lab.result.created},
 * do lab-service phát). Billing lắng nghe để sinh một khoản phí {@code LAB}, {@code sourceRefId}
 * = {@code labId} (backend-spec/06-billing.md §7).
 *
 * <p><b>Khai theo đúng payload thật của lab-service</b> (04-lab.md §8):
 * {@code {envelope, labId, patientId, recordId, departmentId, results, conclusion}}.
 * Billing chỉ cần bốn trường định danh dưới đây; {@code results}/{@code conclusion} bị bỏ qua
 * (Jackson tự lờ trường thừa).
 *
 * <p><b>Loại xét nghiệm để tra giá:</b> payload này KHÔNG mang {@code labType} (trường đó nằm ở
 * {@code lab.request.created} — sự kiện khác). Billing lấy {@code labType} qua
 * {@code LabTestTypePort} (bảng chiếu local nạp từ {@code lab.request.created}), không gọi REST
 * đồng bộ sang lab. Xem {@code THELOC-INTEGRATION-FOLLOWUP.md} mục 2.
 *
 * @param eventId      khóa chống xử lý trùng (BR-B7)
 * @param occurredAt   thời điểm lab trả kết quả
 * @param correlationId mã truy vết xuyên suốt
 * @param labId        phiếu/kết quả xét nghiệm — dùng làm {@code sourceRefId} và để tra {@code labType}
 * @param patientId    bệnh nhân
 * @param recordId     hồ sơ bệnh án liên quan
 * @param departmentId khoa chỉ định xét nghiệm — gắn vào khoản phí (BR-B8)
 */
public record LabResultCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId
) {}
