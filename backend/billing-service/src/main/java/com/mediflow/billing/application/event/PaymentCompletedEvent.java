package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.model.PaymentMethod;

/**
 * Event billing <b>phát</b>: "hóa đơn đã được thanh toán" (routing key {@code payment.completed}).
 * Publish <b>sau khi commit</b> (backend-spec/06-billing.md §7 bước 6, §11) — đây chính là tín
 * hiệu để pharmacy xuất thuốc, nên bắt buộc mang {@code prescriptionId}; thiếu trường này pharmacy
 * phải đoán (§9).
 *
 * @param eventId        khóa để consumer dedupe
 * @param occurredAt     thời điểm thanh toán
 * @param correlationId  mã truy vết xuyên suốt saga
 * @param invoiceId      hóa đơn đã trả
 * @param patientId      bệnh nhân
 * @param departmentId   khoa phát sinh đơn thuốc (report gom theo khoa)
 * @param prescriptionId đơn thuốc cần xuất — pharmacy dựa vào đây
 * @param totalAmount    tổng tiền đã thanh toán
 * @param paymentMethod  hình thức thanh toán (billing sở hữu enum này)
 */
public record PaymentCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID invoiceId,
        UUID patientId,
        UUID departmentId,
        UUID prescriptionId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod
) {}
