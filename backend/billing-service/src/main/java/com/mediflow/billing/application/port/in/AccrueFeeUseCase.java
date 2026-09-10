package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.event.AppointmentStatusChangedEvent;
import com.mediflow.billing.application.event.LabResultCreatedEvent;
import com.mediflow.billing.application.event.MedicalRecordCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;

/**
 * In-port do các consumer sự kiện gọi vào — "sinh viện phí khi có việc xảy ra ở service khác"
 * (backend-spec/06-billing.md §6, §7). {@code FeeAccrualService} (Phần 3/5) hiện thực.
 *
 * <p>Consumer trong {@code messaging/consumer} (Phần 5/5) là driving adapter: nó chỉ chuyển
 * payload RabbitMQ thành các record event ở đây rồi gọi in-port, không chứa logic nghiệp vụ.
 * Mọi handler đều idempotent theo {@code eventId} qua {@code ProcessedEventPort} và kiểm
 * {@code existsBySource} trước khi tạo phí (BR-B7); mọi khoản phí luôn có {@code departmentId}
 * (BR-B8).
 */
public interface AccrueFeeUseCase {

    /** {@code medicalrecord.created} → sinh phí {@code EXAM}, số tiền {@code priceList.examFee(departmentId)}. */
    void onMedicalRecordCreated(MedicalRecordCreatedEvent e);

    /** {@code lab.result.created} → sinh phí {@code LAB}, số tiền {@code priceList.labFee(labType)}. */
    void onLabResultCreated(LabResultCreatedEvent e);

    /** {@code appointment.status.changed} → sinh phí {@code EXAM} khi {@code status == "ARRIVED"} và hồ sơ chưa có phí. */
    void onAppointmentStatusChanged(AppointmentStatusChangedEvent e);

    /** {@code prescription.created} → <b>cửa vào saga</b>: tạo phí {@code DRUG} + hóa đơn {@code AWAITING_PAYMENT} (BR-B6). */
    void onPrescriptionCreated(PrescriptionCreatedEvent e);
}
