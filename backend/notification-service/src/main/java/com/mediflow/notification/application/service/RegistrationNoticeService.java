package com.mediflow.notification.application.service;

import com.mediflow.notification.application.dto.command.RegistrationNoticeCommand;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.RegistrationNoticeProjectionPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import java.util.UUID;

public class RegistrationNoticeService {
    private final CareNotificationRepositoryPort history;
    private final RegistrationNoticeProjectionPort projections;

    public RegistrationNoticeService(CareNotificationRepositoryPort history, RegistrationNoticeProjectionPort projections) {
        this.history = history; this.projections = projections;
    }

    public void receive(RegistrationNoticeCommand command) {
        if (!history.claim(command.eventId(), RegistrationNoticeCommand.EVENT_TYPE, command.rawHash())) return;
        if (!projections.advance(command)) return;
        String title = switch (command.status()) {
            case "AWAITING_PAYMENT" -> "Đăng ký đang chờ thanh toán";
            case "CONFIRMED" -> "Phiếu khám đã xác nhận";
            case "CANCELLED" -> "Phiếu đăng ký đã hủy";
            case "EXPIRED" -> "Phiếu đăng ký đã hết hạn giữ suất";
            case "RECONCILIATION_REQUIRED" -> "Phiếu khám cần đối soát";
            default -> null;
        };
        if (title == null) return;
        String content = switch (command.status()) {
            case "AWAITING_PAYMENT" -> "Đã ghi nhận đăng ký. Vui lòng thanh toán trước hạn giữ suất; phiếu khám chưa được xác nhận.";
            case "CONFIRMED" -> "Phiếu khám đã được xác nhận. Vui lòng xem phòng khám, bác sĩ, khung giờ dự kiến và số thứ tự trên phiếu.";
            case "CANCELLED" -> "Phiếu đăng ký đã hủy. Việc hủy không phải xác nhận đã hoàn tiền; vui lòng liên hệ quầy nếu đã thanh toán.";
            case "EXPIRED" -> "Thời gian giữ suất đã hết. Vui lòng liên hệ quầy tiếp nhận để đăng ký lại.";
            default -> "Hệ thống đang đối soát phiếu khám. Vui lòng liên hệ quầy tiếp nhận; không thanh toán lại khi chưa rõ kết quả.";
        };
        history.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), command.patientId(), command.eventId(),
                RegistrationNoticeCommand.EVENT_TYPE, command.registrationId(), command.correlationId(),
                "OUTPATIENT_REGISTRATION_" + command.status(), title, content));
    }
}
