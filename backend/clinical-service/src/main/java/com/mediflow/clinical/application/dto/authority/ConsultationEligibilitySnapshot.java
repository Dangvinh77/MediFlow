package com.mediflow.clinical.application.dto.authority;

import java.time.Instant;
import java.util.UUID;

public record ConsultationEligibilitySnapshot(
        UUID doctorId,
        Boolean doctorExists,
        Boolean doctorActive,
        Boolean eligibleDoctor,
        UUID doctorDepartmentId,
        UUID departmentId,
        Boolean departmentExists,
        Boolean departmentActive,
        UUID roomId,
        Boolean roomExists,
        Boolean roomActive,
        UUID roomDepartmentId,
        String roomType,
        Boolean eligible,
        Instant observedAt) {
}
