package com.mediflow.pharmacy.domain.model.enums;

/**
 * Trạng thái bằng chứng thanh toán được lưu cục bộ tại pharmacy.
 *
 * <p>{@code RECEIVED} không phải trạng thái terminal: receipt ở trạng thái này cho phép
 * orchestrator tiếp tục xử lý sau khi process bị crash. Hai trạng thái còn lại là terminal
 * và không thể quay ngược.</p>
 */
public enum PaymentReceiptStatus {
    /** Billing event đã được claim nhưng workflow chưa có outcome cuối cùng. */
    RECEIVED,
    /** Đơn thuốc đã được xuất thành công sau khi thanh toán. */
    DISPENSED,
    /** Thanh toán đã kết thúc bằng nhánh bù trừ. */
    COMPENSATED
}
