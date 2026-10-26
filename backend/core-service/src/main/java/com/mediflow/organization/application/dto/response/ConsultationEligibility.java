package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConsultationEligibility(
        UUID doctorId,
        boolean doctorExists,
        boolean doctorActive,
        boolean eligibleDoctor,
        UUID doctorDepartmentId,
        UUID departmentId,
        boolean departmentExists,
        boolean departmentActive,
        UUID roomId,
        boolean roomExists,
        boolean roomActive,
        UUID roomDepartmentId,
        String roomType,
        boolean eligible,
        Instant observedAt) {}
