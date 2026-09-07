package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;

/**
 * In-port cho hai nhánh kết thúc của saga kê đơn → hóa đơn → thanh toán → xuất thuốc
 * (backend-spec/06-billing.md §3, §7). {@code SagaCompensationService} (Phần 3/5) hiện thực.
 * Do consumer {@code prescription.filled} / {@code prescription.dispense.failed} gọi vào;
 * cả hai đều idempotent theo {@code eventId}.
 */
public interface SagaCompensationUseCase {

    /**
     * Nhánh <b>bù trừ</b>: xuất thuốc thất bại → đảo thanh toán của hóa đơn (đặt {@code isPaid = false}),
     * chuyển saga sang {@code REFUNDED}, {@code refund()} mọi khoản phí đính kèm, rồi publish
     * {@code payment.failed} (BR-B4/BR-B5). Không có hóa đơn tương ứng thì ghi log rồi bỏ qua.
     */
    void onDispenseFailed(PrescriptionDispenseFailedEvent e);

    /**
     * Nhánh <b>thành công</b>: xuất thuốc xong → chuyển saga của hóa đơn sang {@code COMPLETED}
     * (BR-B11). Không publish event nào.
     */
    void onPrescriptionFilled(PrescriptionFilledEvent e);
}
