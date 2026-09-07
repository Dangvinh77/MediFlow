package com.mediflow.billing.application.port.in;

import java.util.UUID;

import com.mediflow.billing.application.dto.request.CreateInvoiceRequest;
import com.mediflow.billing.application.dto.request.PayInvoiceRequest;
import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.application.dto.response.PaymentResultDTO;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * In-port — "bảng công việc quản lý hóa đơn" mà web/controller nhờ application làm
 * (backend-spec/06-billing.md §6, §8). Đây chỉ là hợp đồng — {@code BillingApplicationService}
 * (Phần 3/5) sẽ hiện thực. Driving adapter (controller) luôn gọi qua in-port này, không gọi
 * thẳng application service.
 */
public interface ManageInvoiceUseCase {

    /**
     * Lập hóa đơn thường: gộp mọi khoản phí chưa thanh toán của bệnh nhân, tổng tiền do server
     * tính (BR-B2). Không còn khoản phí chưa trả nào → ném {@code BILLING_NO_UNPAID_FEES} (422).
     */
    InvoiceDTO create(CreateInvoiceRequest r);

    /** Xem một hóa đơn theo id. Không có → {@code InvoiceNotFoundException} → 404. */
    InvoiceDTO getById(UUID id);

    /** Danh sách hóa đơn của một bệnh nhân, phân trang bằng {@link PageQuery}/{@link PageResult}. */
    PageResult<InvoiceDTO> byPatient(UUID patientId, PageQuery page);

    /**
     * Thanh toán hóa đơn {@code id}. Ném {@code BILLING_ALREADY_PAID} nếu đã trả (BR-B1); đánh
     * dấu mọi khoản phí liên quan là đã trả (BR-B3); nếu là hóa đơn từ đơn thuốc thì đẩy saga
     * sang {@code AWAITING_DISPENSE} và publish {@code payment.completed} sau commit (§7).
     */
    PaymentResultDTO pay(UUID id, PayInvoiceRequest r);
}
