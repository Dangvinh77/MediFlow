package com.mediflow.pharmacy.domain.model.enums;

/**
 * Nguyên nhân kết thúc một giữ chỗ mà không xuất thuốc.
 *
 * <p>Giá trị này phục vụ audit và phân biệt giải phóng chủ động ({@code RELEASED}) với hết hạn
 * tự động ({@code EXPIRED}). Bốn giá trị đầu là các nguyên nhân runtime được phép giải phóng
 * trước hạn; {@link #TTL_EXPIRED} chỉ dùng bởi luồng TTL và {@link #LEGACY_MIGRATION} chỉ dùng
 * khi chuyển đổi dữ liệu cũ.</p>
 */
public enum ReservationReleaseReason {
    /** Bác sĩ hoặc quản trị viên hủy đơn thuốc. */
    PRESCRIPTION_CANCELLED,
    /** Thanh toán thất bại nên không tiếp tục giữ kho. */
    PAYMENT_FAILED,
    /** Xuất thuốc thất bại và hệ thống thực hiện bù trừ. */
    DISPENSE_FAILED,
    /** Quản trị viên giải phóng thủ công có lý do. */
    ADMIN_OVERRIDE,
    /** Hệ thống giải phóng vì thời gian giữ chỗ đã hết. */
    TTL_EXPIRED,
    /** Dữ liệu RELEASED có trước khi bổ sung trường audit. */
    LEGACY_MIGRATION
}
