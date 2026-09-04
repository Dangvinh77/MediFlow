package com.mediflow.pharmacy.domain.model.enums;

/**
 * Trạng thái vòng đời của một đơn thuốc.
 *
 * <p>Đơn mới luôn ở {@link #ACTIVE}. Từ trạng thái này, đơn chỉ được kết thúc theo một trong
 * bốn nhánh: xuất thành công, hủy chủ động, hết hạn giữ chỗ hoặc xuất thất bại. Các trạng thái
 * kết thúc không được chuyển tiếp lần nữa.</p>
 */
public enum PrescriptionStatus {
    /** Đơn đang hoạt động và các dòng thuốc còn được giữ chỗ. */
    ACTIVE,
    /** Toàn bộ thuốc trong đơn đã được xuất thành công. */
    FULFILLED,
    /** Đơn bị hủy chủ động trước khi xuất thuốc. */
    CANCELLED,
    /** Thời gian giữ chỗ của đơn đã hết. */
    EXPIRED,
    /** Quy trình xuất thuốc kết thúc với lỗi và cần bù trừ. */
    DISPENSE_FAILED
}
