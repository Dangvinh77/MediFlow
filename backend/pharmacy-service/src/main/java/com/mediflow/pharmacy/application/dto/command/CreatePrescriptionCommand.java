package com.mediflow.pharmacy.application.dto.command;

import java.util.Objects;
import java.util.UUID;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
/**
 * Lệnh tạo đơn thuốc với danh tính người thực hiện lấy từ JWT.
 *
 * @param request dữ liệu đơn thuốc
 * @param actor danh tính đã tách accountId/staffId/role từ JWT đã xác thực
 * @param correlationId mã tương quan của request
 */

public record CreatePrescriptionCommand(
    CreatePrescriptionRequest request,
    ActorIdentity actor,
    String correlationId
) {
    public CreatePrescriptionCommand {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(actor, "actor is required");
        correlationId = normalizeCorrelationId(correlationId);
    }

    /** Chuẩn hóa correlation để event và HTTP response luôn có mã truy vết hợp lệ. */
    private static String normalizeCorrelationId(String value) {
        return value == null || value.isBlank()
                ? UUID.randomUUID().toString()
                : value.trim();
    }
}
