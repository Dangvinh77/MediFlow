package com.mediflow.pharmacy.application.dto.command;

import java.util.UUID;

/**
 * Lệnh hủy một đơn thuốc đang giữ chỗ tồn kho.
 *
 * <p>Thông tin người thực hiện và quyền quản trị được driving adapter dựng từ JWT đã xác
 * thực, không nhận trực tiếp từ request body. Nhờ đó client không thể tự khai báo danh tính
 * hoặc nâng quyền khi yêu cầu hủy đơn.</p>
 *
 * @param prescriptionId mã đơn thuốc cần hủy
 * @param actorId mã người dùng thực hiện thao tác, lấy từ JWT subject
 * @param administrator {@code true} khi người thực hiện có role {@code ADMIN}
 * @param reason lý do hủy do người dùng cung cấp
 * @param correlationId mã tương quan của request để truyền sang domain event
 */
public record CancelPrescriptionCommand(
        UUID prescriptionId,
        UUID actorId,
        boolean administrator,
        String reason,
        String correlationId
) {
}
