package com.mediflow.pharmacy.application.dto.command;

import java.util.Objects;
import java.util.UUID;

import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
/**
 * Lệnh tạo đơn thuốc với danh tính người thực hiện lấy từ JWT.
 *
 * @param request dữ liệu đơn thuốc
 * @param actorId người dùng thực hiện, lấy từ JWT subject
 * @param administrator true nếu người thực hiện có role ADMIN
 * @param correlationId mã tương quan của request
 */

public record CreatePrescriptionCommand(
    CreatePrescriptionRequest request,
    UUID actorId,
    boolean administrator,
    String correlationId
) {
    public CreatePrescriptionCommand {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(actorId, "actorId is required");
    }
}
