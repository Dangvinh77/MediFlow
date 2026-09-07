package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.billing.domain.model.FeeType;

/**
 * Event billing <b>phát</b>: "một hóa đơn vừa được lập" (routing key {@code invoice.created}).
 * Publish sau khi transaction tạo hóa đơn commit (backend-spec/06-billing.md §9, §11).
 * Do out-port {@code BillingEventPublisherPort} công bố; adapter RabbitMQ thật nằm ở
 * {@code infrastructure/messaging} (làm ở Phần 5/5).
 *
 * @param eventId       khóa để consumer dedupe
 * @param occurredAt    thời điểm lập hóa đơn
 * @param correlationId mã truy vết xuyên suốt
 * @param invoiceId     hóa đơn vừa lập
 * @param patientId     bệnh nhân của hóa đơn
 * @param departmentId  khoa phát sinh phần lớn khoản phí (report gom theo khoa)
 * @param totalAmount   tổng tiền hóa đơn — server tự tính từ các khoản phí (BR-B2)
 * @param items         chi tiết từng khoản phí trong hóa đơn
 */
public record InvoiceCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID patientId,
        UUID departmentId,
        BigDecimal totalAmount,
        List<Item> items
) {
    /** Một khoản phí trong hóa đơn. */
    public record Item(UUID feeId, FeeType type, BigDecimal amount) {}
}
