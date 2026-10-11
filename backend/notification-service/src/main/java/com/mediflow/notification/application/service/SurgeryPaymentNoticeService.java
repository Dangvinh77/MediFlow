package com.mediflow.notification.application.service;

import com.mediflow.notification.application.dto.command.SurgeryPaymentNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToSurgeryPaymentNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import java.util.UUID;

public final class SurgeryPaymentNoticeService implements ReactToSurgeryPaymentNoticeUseCase {
    private final CareNotificationRepositoryPort history;
    private final NotificationSourcePort sources;
    public SurgeryPaymentNoticeService(CareNotificationRepositoryPort history, NotificationSourcePort sources) {
        this.history = history; this.sources = sources;
    }
    @Override public void receive(SurgeryPaymentNoticeCommand command) {
        if (!history.claim(command.eventId(), "invoice.created", command.deliveryFingerprint())) return;
        if (!sources.claim("invoice.created", command.paymentRequestId(), command.sourceFingerprint())) return;
        boolean prescription = "PRESCRIPTION".equals(command.purpose());
        history.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), command.patientId(), command.eventId(),
                "invoice.created", command.paymentRequestId(), command.correlationId(), prescription ? "PRESCRIPTION_PAYMENT_REQUEST" : "SURGERY_PAYMENT_REQUEST",
                prescription ? "Yêu cầu thanh toán đơn thuốc" : "Yêu cầu thanh toán phẫu thuật",
                "Khoản thanh toán được yêu cầu: " + command.totalAmount().toPlainString() + " " + command.currency()
                + ". Vui lòng liên hệ quầy thu ngân. " + (prescription
                    ? "Đây không phải biên nhận đã thanh toán và không xác nhận thuốc đã được cấp."
                    : "Đây không phải biên nhận đã thanh toán hoặc xác nhận lịch mổ.")));
    }
}
