package com.mediflow.pharmacy.domain.model.enums;

/**
 * Trạng thái một dòng giữ chỗ tồn kho (STOCK_RESERVATION).
 *
 * <p>Vòng đời:
 * <pre>
 *   RESERVED --xuất thành công--> FULFILLED   (đã trừ kho thật, kết thúc)
 *   RESERVED --hủy / không thanh toán--> RELEASED  (trả lại chỗ)
 *   RESERVED --hết hạn TTL--> EXPIRED             (trả lại chỗ)
 * </pre>
 * Chỉ {@code RESERVED} mới được tính vào "đang giữ chỗ" (ảnh hưởng số tồn có thể bán).
 */
public enum ReservationStatus {
    RESERVED,   // đang giữ chỗ — chưa xuất, đơn chưa thanh toán
    FULFILLED,  // đã xuất thuốc thật (reserved → stock)
    RELEASED,   // trả lại chỗ (hủy đơn / chủ động giải phóng)
    EXPIRED;    // trả lại chỗ do hết hạn TTL
}
