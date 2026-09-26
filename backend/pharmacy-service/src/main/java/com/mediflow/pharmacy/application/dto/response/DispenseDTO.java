package com.mediflow.pharmacy.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.DispenseActorType;

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
 * @param dispensedActorType kind of identity stored in dispensedBy
 * @param failureReason terminal failure reason, if any
 */
public record DispenseDTO(
        UUID dispenseId,
        UUID prescriptionId,
        DispenseStatus status,
        Instant dispensedAt,
        UUID dispensedBy,
        DispenseActorType dispensedActorType,
        String failureReason
) {
    /** Compatibility constructor for callers that do not yet carry actor-kind metadata. */
    public DispenseDTO(
            UUID dispenseId,
            UUID prescriptionId,
            DispenseStatus status,
            Instant dispensedAt,
            UUID dispensedBy,
            String failureReason) {
        this(dispenseId, prescriptionId, status, dispensedAt, dispensedBy,
                dispensedBy == null ? null : DispenseActorType.LEGACY_UNKNOWN, failureReason);
    }
}
