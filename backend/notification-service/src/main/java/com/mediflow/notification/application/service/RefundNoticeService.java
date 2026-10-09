package com.mediflow.notification.application.service;

import java.util.UUID;
import com.mediflow.notification.application.dto.command.RefundNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToRefundNoticeUseCase;
import com.mediflow.notification.application.port.out.*;
import com.mediflow.notification.domain.model.CareNotificationIntent;

public class RefundNoticeService implements ReactToRefundNoticeUseCase {
    private final CareNotificationRepositoryPort history;
    private final NotificationSourcePort sources;
    public RefundNoticeService(CareNotificationRepositoryPort history, NotificationSourcePort sources) {
        this.history = history; this.sources = sources;
    }
    @Override public void receive(RefundNoticeCommand command) {
        if (!history.claim(command.eventId(), "payment.refunded", command.deliveryFingerprint())) return;
        if (!sources.claim("payment.refunded", command.refundTransactionId(), command.sourceFingerprint())) return;
        history.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), command.patientId(), command.eventId(),
                "payment.refunded", command.refundTransactionId(), command.correlationId(), "PAYMENT_REFUNDED",
                "Biên nhận hoàn tiền", "Thu ngân đã ghi nhận hoàn tiền " + command.amount().setScale(2).toPlainString() + " " + command.currency()
                + " cho giao dịch " + command.originalTransactionId() + ". Đây không phải quyết toán cuối cùng hoặc xác nhận trạng thái điều trị."));
    }
}
