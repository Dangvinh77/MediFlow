package com.mediflow.notification.application.service;

import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToSurgeryNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.SurgeryNoticeStatePort;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import java.util.UUID;

/** Private, redacted provisional history; terminal/invalidation evidence never authorizes care. */
public final class SurgeryNoticeService implements ReactToSurgeryNoticeUseCase {
    private final CareNotificationRepositoryPort history;
    private final SurgeryNoticeStatePort state;

    public SurgeryNoticeService(CareNotificationRepositoryPort history, SurgeryNoticeStatePort state) {
        this.history = history; this.state = state;
    }

    @Override public void receive(SurgeryNoticeCommand command) {
        if (!history.claim(command.eventId(), command.eventType(), command.deliveryFingerprint())) return;
        state.lockExactCase(command.context());
        if (!state.recordSource(command)) return;
        switch (command.eventType()) {
            case "surgery.ready" -> {
                var intent = intent(command, "SURGERY_READY", "Thông tin lịch mổ dự kiến",
                        "Dự kiến bắt đầu: " + command.plannedStartAt()
                        + ". Đây chưa phải lịch đặt phòng đã xác nhận. Liên hệ khoa điều trị để xác nhận lịch hiện tại.");
                if (state.readyIsSuppressed(command)) history.recordSuppressedInApp(intent);
                else history.deliverInApp(intent);
            }
            case "surgery.readiness.invalidated" -> state.invalidateSnapshot(command);
            case "surgery.cancelled" -> {
                state.markTerminal(command);
                history.deliverInApp(intent(command, "SURGERY_CANCELLED", "Thông báo hủy lịch mổ",
                        "Ca phẫu thuật đã được hủy trước khi bắt đầu. Liên hệ khoa điều trị để được hướng dẫn."));
            }
            case "surgery.completed" -> state.markTerminal(command);
            default -> throw new IllegalArgumentException("Unsupported Surgery notice");
        }
    }

    private CareNotificationIntent intent(SurgeryNoticeCommand command, String template, String title, String content) {
        return new CareNotificationIntent(UUID.randomUUID(), command.context().patientId(), command.eventId(),
                command.eventType(), command.sourceId(), command.correlationId(), template, title, content);
    }
}
