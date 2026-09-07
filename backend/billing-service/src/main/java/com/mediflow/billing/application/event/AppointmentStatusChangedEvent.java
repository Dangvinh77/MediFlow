package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ: "trạng thái một lịch khám vừa đổi" (routing key {@code appointment.status.changed},
 * do clinical-service phát).
 *
 * <p><b>Khai theo đúng payload thật của clinical-service</b> (03-clinical.md §9):
 * {@code {envelope, appointmentId, status, patientId, departmentId}}. Payload này KHÔNG mang
 * {@code recordId}.
 *
 * <p><b>V1 — billing KHÔNG sinh phí từ event này.</b> Phí khám (EXAM) được sinh dứt điểm từ
 * {@code medicalrecord.created} ({@code sourceRefId = recordId}); sinh thêm một phí EXAM từ nhánh
 * ARRIVED sẽ tính tiền hai lần cho cùng một lượt khám, vì hai event hiện chưa có id chung để
 * chống trùng chéo (BR-B7). Cùng tinh thần lab-service ("V1: bỏ qua nếu payload không đủ dữ
 * liệu"). Xem {@code THELOC-INTEGRATION-FOLLOWUP.md} mục 1.
 *
 * @param eventId       khóa chống xử lý trùng
 * @param occurredAt    thời điểm đổi trạng thái
 * @param correlationId mã truy vết xuyên suốt
 * @param appointmentId lịch khám đổi trạng thái
 * @param patientId     bệnh nhân
 * @param departmentId  khoa khám
 * @param status        trạng thái mới ({@code PENDING | ARRIVED | CANCELLED})
 */
public record AppointmentStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID appointmentId,
        UUID patientId,
        UUID departmentId,
        String status
) {}
