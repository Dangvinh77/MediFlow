package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;

/**
 * DTO đầy đủ của một hóa đơn trả về cho client (backend-spec/06-billing.md §8), gồm cả danh sách
 * khoản phí thành phần. Mirror các trường của {@code Invoice} cộng {@code fees}; {@code Invoice}
 * (domain) không bao giờ đi thẳng ra ngoài.
 *
 * @param invoiceId      mã hóa đơn
 * @param patientId      bệnh nhân
 * @param createdDate    ngày lập hóa đơn
 * @param totalAmount    tổng tiền (server tính, BR-B2)
 * @param isPaid         đã thanh toán chưa
 * @param paymentMethod  hình thức thanh toán (null khi chưa trả)
 * @param dispenseId     phiếu xuất thuốc gắn kèm khi saga hoàn tất (null nếu không có)
 * @param prescriptionId đơn thuốc mở saga (null với hóa đơn thường)
 * @param sagaStatus     bước saga hiện tại ({@code NONE} với hóa đơn thường)
 * @param paidAt         thời điểm thanh toán (null khi chưa trả)
 * @param fees           chi tiết các khoản phí trong hóa đơn
 */
public record InvoiceDTO(
        UUID invoiceId,
        UUID patientId,
        LocalDate createdDate,
        BigDecimal totalAmount,
        boolean isPaid,
        PaymentMethod paymentMethod,
        UUID dispenseId,
        UUID prescriptionId,
        SagaStatus sagaStatus,
        Instant paidAt,
        List<FeeDTO> fees
) {}
