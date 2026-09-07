package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event billing <b>phát</b>: "thanh toán không thành công / đã bị đảo" (routing key
 * {@code payment.failed}). Publish trong nhánh bù trừ saga khi xuất thuốc thất bại
 * (backend-spec/06-billing.md §7 "onDispenseFailed" bước 6, BR-B5) — notification-service
 * lắng nghe để báo bệnh nhân.
 *
 * @param eventId       khóa để consumer dedupe
 * @param occurredAt    thời điểm đảo thanh toán
 * @param correlationId mã truy vết xuyên suốt saga
 * @param invoiceId     hóa đơn đã bị đảo về chưa thanh toán
 * @param patientId     bệnh nhân
 * @param reason        lý do (chuyển tiếp từ {@code prescription.dispense.failed})
 */
public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID patientId,
        String reason
) {}
