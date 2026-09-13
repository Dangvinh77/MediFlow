package com.mediflow.pharmacy.application.port.out;

/** Kết quả claim receipt, phân biệt nhận mới, giao lại giống hệt và payload xung đột. */
public enum PaymentReceiptClaimStatus {
    /** Receipt mới được tạo và caller sở hữu quyền tiếp tục xử lý. */
    CLAIMED,
    /** Event đã tồn tại với payload giống nhau; caller có thể resume theo receipt hiện tại. */
    DUPLICATE_SAME,
    /** Event đã tồn tại nhưng payload khác; không được xử lý hoặc ghi đè. */
    DUPLICATE_CONFLICT
}
