package com.mediflow.notification.application.service;

import java.util.UUID;

import com.mediflow.notification.application.dto.command.AdmissionClosedCommand;
import com.mediflow.notification.application.dto.command.AdmissionDepositRequestedCommand;
import com.mediflow.notification.application.dto.command.AdmissionStartedCommand;
import com.mediflow.notification.application.dto.command.SurgeryCancelledNoticeCommand;
import com.mediflow.notification.application.dto.command.SurgeryReadyCommand;
import com.mediflow.notification.application.port.in.ReactToCareProjectionUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;

/**
 * Hiện thực {@link ReactToCareProjectionUseCase} — biến các projection fact (đặt cọc, nhập viện,
 * xuất viện, lịch mổ sẵn sàng, hủy mổ) thành thông báo IN_APP. Không bao giờ query Patient DB để
 * lấy liên hệ (BR theo CONTRACT-CARE-PROJECTIONS-01) — V1 chấp nhận {@code IN_APP} only, giống
 * {@link CarePaymentReceiptService}.
 */
public class CareProjectionService implements ReactToCareProjectionUseCase {
    private final CareNotificationRepositoryPort repository;

    public CareProjectionService(CareNotificationRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void onAdmissionDepositRequested(AdmissionDepositRequestedCommand c) {
        if (!repository.claim(c.eventId(), "admission.deposit.requested", c.fingerprint())) return;
        String amount = c.suggestedAmount().toPlainString() + " VND";
        String content = "Bệnh viện đề nghị đặt cọc " + amount + " cho đợt nhập viện."
                + (blank(c.reason()) ? "" : " Lý do: " + c.reason());
        deliver(c.eventId(), "admission.deposit.requested", c.admissionId(), c.patientId(), c.correlationId(),
                "ADMISSION_DEPOSIT_REQUESTED", "Yêu cầu đặt cọc nhập viện", content);
    }

    @Override
    public void onAdmissionStarted(AdmissionStartedCommand c) {
        if (!repository.claim(c.eventId(), "admission.started", c.fingerprint())) return;
        deliver(c.eventId(), "admission.started", c.admissionId(), c.patientId(), c.correlationId(),
                "ADMISSION_STARTED", "Đã nhập viện",
                "Hồ sơ nhập viện của bạn đã được ghi nhận lúc " + c.admittedAt() + ".");
    }

    @Override
    public void onAdmissionClosed(AdmissionClosedCommand c) {
        if (!repository.claim(c.eventId(), "admission.closed", c.fingerprint())) return;
        deliver(c.eventId(), "admission.closed", c.admissionId(), c.patientId(), c.correlationId(),
                "ADMISSION_CLOSED", "Đã đóng hồ sơ nhập viện",
                "Hồ sơ nhập viện đã được đóng lúc " + c.closedAt() + ". Vui lòng kiểm tra quyết toán viện phí.");
    }

    @Override
    public void onSurgeryReady(SurgeryReadyCommand c) {
        if (!repository.claim(c.eventId(), "surgery.ready", c.fingerprint())) return;
        deliver(c.eventId(), "surgery.ready", c.surgeryCaseId(), c.patientId(), c.correlationId(),
                "SURGERY_READY", "Lịch mổ đã sẵn sàng",
                "Ca mổ dự kiến bắt đầu lúc " + c.plannedStartAt() + ".");
    }

    @Override
    public void onSurgeryCancelled(SurgeryCancelledNoticeCommand c) {
        if (!repository.claim(c.eventId(), "surgery.cancelled", c.fingerprint())) return;
        String content = "Ca mổ đã bị hủy trước khi bắt đầu."
                + (blank(c.reason()) ? "" : " Lý do: " + c.reason());
        deliver(c.eventId(), "surgery.cancelled", c.surgeryCaseId(), c.patientId(), c.correlationId(),
                "SURGERY_CANCELLED", "Thông báo về ca mổ", content);
    }

    private void deliver(UUID eventId, String eventType, UUID sourceId, UUID patientId, String correlationId,
                         String templateKey, String title, String content) {
        repository.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), patientId, eventId, eventType,
                sourceId, correlationId, templateKey, title, content));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
