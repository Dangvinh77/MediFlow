package com.mediflow.billing.application.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/**
 * Request lập một hóa đơn thường cho bệnh nhân (backend-spec/06-billing.md §8).
 *
 * <p><b>Cố ý không có số tiền:</b> server tự cộng tất cả khoản phí chưa thanh toán của bệnh nhân
 * để ra {@code totalAmount} (BR-B2). Bệnh nhân không còn khoản phí chưa trả nào sẽ nhận lỗi
 * {@code BILLING_NO_UNPAID_FEES} (422), không phải một hóa đơn tổng bằng 0 (§11).
 *
 * <p>Bean Validation ở đây chỉ là tuyến phòng thủ tại biên; quy tắc nghiệp vụ thật nằm trong
 * {@code Invoice.create}.
 *
 * @param patientId   bệnh nhân được lập hóa đơn
 * @param createdDate ngày lập hóa đơn, không ở tương lai
 */
public record CreateInvoiceRequest(
        @NotNull UUID patientId,
        @NotNull @PastOrPresent LocalDate createdDate
) {}
