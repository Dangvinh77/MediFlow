package com.mediflow.pharmacy.domain.model.enums;

/** Trạng thái vòng đời của phiếu xuất thuốc. */
public enum DispenseStatus {
    /** Phiếu đang chờ thanh toán hoặc xuất thuốc. */
    PENDING,
    /** Thuốc đã được xuất thành công. */
    DISPENSED,
    /** Xuất thuốc thất bại và cần bù trừ. */
    FAILED,
    /** Đơn bị hủy chủ động trước khi xuất. */
    CANCELLED,
    /** Giữ chỗ hết TTL trước khi xuất. */
    EXPIRED
}
