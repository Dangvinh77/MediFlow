package com.mediflow.notification.application.service;

import java.util.UUID;
import com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToPrescriptionNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.domain.model.CareNotificationIntent;

public class PrescriptionNoticeService implements ReactToPrescriptionNoticeUseCase {
    private final CareNotificationRepositoryPort history;
    private final NotificationSourcePort sources;

    public PrescriptionNoticeService(CareNotificationRepositoryPort history, NotificationSourcePort sources) {
        this.history = history;
        this.sources = sources;
    }

    @Override public void receive(PrescriptionNoticeCommand command) {
        if (!history.claim(command.eventId(), command.eventType(), command.deliveryFingerprint())) return;
        if (!sources.claim(command.eventType(), command.prescriptionId(), command.sourceFingerprint())) return;
        String template = switch (command.eventType()) {
            case "prescription.created" -> "OUTPATIENT_PRESCRIPTION_CREATED";
            case "prescription.filled" -> "OUTPATIENT_PRESCRIPTION_FILLED";
            case "prescription.cancelled" -> "OUTPATIENT_PRESCRIPTION_CANCELLED";
            case "prescription.expired" -> "OUTPATIENT_PRESCRIPTION_EXPIRED";
            case "prescription.dispense.failed" -> "OUTPATIENT_PRESCRIPTION_DISPENSE_FAILED";
            default -> throw new IllegalArgumentException("Unsupported prescription notice");
        };
        String title = switch (command.eventType()) {
            case "prescription.created" -> "Đã ghi nhận đơn thuốc";
            case "prescription.filled" -> "Đã cấp thuốc";
            case "prescription.cancelled" -> "Đơn thuốc đã hủy";
            case "prescription.expired" -> "Đơn thuốc đã hết hạn";
            default -> "Chưa thể cấp thuốc";
        };
        String content = switch (command.eventType()) {
            case "prescription.created" -> "Đơn thuốc đã được ghi nhận. Vui lòng xem viện phí và hướng dẫn nhận thuốc.";
            case "prescription.filled" -> "Nhà thuốc đã ghi nhận cấp thuốc cho đơn của bạn.";
            case "prescription.cancelled" -> "Đơn thuốc đã được hủy. Vui lòng liên hệ nhà thuốc nếu cần hỗ trợ.";
            case "prescription.expired" -> "Đơn thuốc đã hết thời gian giữ thuốc. Vui lòng liên hệ nhà thuốc.";
            default -> "Nhà thuốc chưa thể cấp thuốc cho đơn của bạn. Vui lòng liên hệ nhà thuốc để được hỗ trợ.";
        };
        history.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), command.patientId(), command.eventId(),
                command.eventType(), command.prescriptionId(), command.correlationId(), template, title, content));
    }
}
