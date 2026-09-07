package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ: "một hồ sơ bệnh án vừa được tạo" (routing key {@code medicalrecord.created},
 * do clinical-service phát). Billing lắng nghe để sinh một khoản phí {@code EXAM}
 * (backend-spec/06-billing.md §7, bảng "Sinh viện phí từ ba event còn lại").
 *
 * <p>Đây là contract qua JSON, không phải import từ clinical-service: mỗi service tự khai lại
 * record cho event nó tiêu thụ (06-billing.md §12.1). Ba trường đầu là envelope chuẩn của mọi
 * event (docs/ai/06-events-rabbitmq.md); {@code departmentId} luôn có mặt vì report gom nhóm
 * doanh thu theo khoa.
 *
 * @param eventId      khóa chống xử lý trùng — consumer dedupe theo trường này (BR-B7)
 * @param occurredAt   thời điểm clinical tạo hồ sơ
 * @param correlationId mã truy vết xuyên suốt
 * @param recordId     hồ sơ bệnh án — dùng làm {@code sourceRefId} của khoản phí EXAM
 * @param patientId    bệnh nhân của hồ sơ
 * @param departmentId khoa lập hồ sơ — gắn vào khoản phí (BR-B8)
 */
public record MedicalRecordCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID recordId,
        UUID patientId,
        UUID departmentId
) {}
