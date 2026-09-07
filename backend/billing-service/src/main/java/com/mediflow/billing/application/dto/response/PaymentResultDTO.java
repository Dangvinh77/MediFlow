package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;

/**
 * Kết quả trả về sau khi gọi {@code PUT /invoices/{id}/pay} (backend-spec/06-billing.md §8).
 * Gọn hơn {@link InvoiceDTO}: chỉ xác nhận thanh toán đã xong và saga đã bước tiếp tới đâu.
 *
 * @param invoiceId     hóa đơn vừa thanh toán
 * @param success       luôn {@code true} khi trả về bình thường (lỗi đi theo exception → HTTP status)
 * @param totalAmount   tổng tiền đã thanh toán
 * @param paymentMethod hình thức thanh toán đã dùng
 * @param paidAt        thời điểm thanh toán
 * @param sagaStatus    bước saga sau thanh toán ({@code AWAITING_DISPENSE} nếu là hóa đơn từ đơn thuốc)
 */
public record PaymentResultDTO(
        UUID invoiceId,
        boolean success,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        Instant paidAt,
        SagaStatus sagaStatus
) {}
