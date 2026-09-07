package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ: "trạng thái một lịch khám vừa đổi" (routing key {@code appointment.status.changed},
 * do clinical-service phát). Billing chỉ quan tâm khi {@code status == "ARRIVED"}: sinh một khoản
 * phí {@code EXAM} nếu hồ sơ đó chưa có phí khám (backend-spec/06-billing.md §7).
 *
 * <p>Contract qua JSON — billing tự khai lại record này (06-billing.md §12.1). {@code status}
 * để kiểu {@link String} để billing không phụ thuộc enum trạng thái của clinical-service.
 *
 * @param eventId       khóa chống xử lý trùng (BR-B7)
 * @param occurredAt    thời điểm đổi trạng thái
 * @param correlationId mã truy vết xuyên suốt
 * @param appointmentId lịch khám đổi trạng thái
 * @param recordId      hồ sơ bệnh án gắn với lịch khám — dùng làm {@code sourceRefId}
 * @param patientId     bệnh nhân
 * @param departmentId  khoa khám — gắn vào khoản phí (BR-B8)
 * @param status        trạng thái mới; billing chỉ hành động khi {@code "ARRIVED"}
 */
public record AppointmentStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID appointmentId,
        UUID recordId,
        UUID patientId,
        UUID departmentId,
        String status
) {}
