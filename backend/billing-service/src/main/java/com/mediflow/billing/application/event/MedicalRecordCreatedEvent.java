package com.mediflow.billing.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event tiêu thụ: "một hồ sơ bệnh án vừa được tạo" (routing key {@code medicalrecord.created},
 * do clinical-service phát). Billing lắng nghe để sinh một khoản phí {@code EXAM},
 * {@code sourceRefId} = {@code recordId} (backend-spec/06-billing.md §7). Đây là nguồn sinh phí
 * khám dứt điểm, đã đủ id chống trùng (BR-B7).
 *
 * <p><b>Khai theo đúng payload thật của clinical-service</b> (03-clinical.md §9):
 * {@code {envelope, recordId, patientId, doctorId, departmentId, diagnosis, examinationDate}}.
 * Billing dùng {@code examinationDate} làm ngày phát sinh khoản phí; {@code doctorId} và
 * {@code diagnosis} bị bỏ qua.
 *
 * @param eventId         khóa chống xử lý trùng (BR-B7)
 * @param occurredAt      thời điểm clinical tạo hồ sơ
 * @param correlationId   mã truy vết xuyên suốt
 * @param recordId        hồ sơ bệnh án — {@code sourceRefId} của khoản phí EXAM
 * @param patientId       bệnh nhân của hồ sơ
 * @param departmentId    khoa lập hồ sơ — gắn vào khoản phí (BR-B8)
 * @param examinationDate ngày khám — ngày phát sinh khoản phí EXAM
 */
public record MedicalRecordCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID recordId,
        UUID patientId,
        UUID departmentId,
        LocalDate examinationDate
) {}
