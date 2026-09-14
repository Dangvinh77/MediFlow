package com.mediflow.pharmacy.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

/**
 * DTO trả về khi xuất thuốc ({@code DispensePrescriptionUseCase.dispense}).
 * Mirror đúng model {@code DispenseSlip}: khi thành công thì status = DISPENSED kèm
 * dispensedAt/dispensedBy; khi thất bại thì status = FAILED kèm failureReason.
 * DTO chỉ mang dữ liệu, không có quy tắc nghiệp vụ.
 *
 * @param dispenseId dispense slip identifier
 * @param prescriptionId prescription identifier
 * @param status current dispense status
 * @param dispensedAt completion timestamp
 * @param dispensedBy actor identifier
 * @param failureReason terminal failure reason, if any
 */
public record DispenseDTO(
        UUID dispenseId,
        UUID prescriptionId,
        DispenseStatus status,
        Instant dispensedAt,
        UUID dispensedBy,
        String failureReason
) {}
