package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event tiêu thụ: ca mổ bị hủy <b>trước khi bắt đầu</b> (routing key {@code surgery.cancelled},
 * do surgery-service phát — chỉ có BEFORE_PREOP/AFTER_PREOP/BEFORE_START ở V1).
 * CONTRACT-SURGERY-BILLING-01 "Cancellation and refund": Billing hủy charge chưa phân bổ thanh
 * toán, hoặc tạo giao dịch hoàn tiền cho phần đã thanh toán — không bao giờ sửa/xóa payment đã
 * hoàn tất. {@code cancellationId} bất biến là khóa chống xử lý trùng/xung đột.
 *
 * <p>JSON là envelope lồng (nested) giống {@link SurgeryCaseCreatedEvent} — xem
 * {@code surgery-outcomes-v1/surgery.cancelled.*.v1.json}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgeryCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public UUID surgeryCaseId() { return payload.surgeryCaseId(); }
    public UUID cancellationId() { return payload.cancellationId(); }
    public String cancellationStage() { return payload.cancellationStage(); }
    public String reason() { return payload.reason(); }
    public Instant cancelledAt() { return payload.cancelledAt(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID surgeryCaseId,
            UUID admissionId,
            UUID recordId,
            UUID cancellationId,
            String cancellationStage,
            String reason,
            UUID cancelledBy,
            Instant cancelledAt
    ) {}
}
