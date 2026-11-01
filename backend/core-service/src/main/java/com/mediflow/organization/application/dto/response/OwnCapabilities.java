package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OwnCapabilities(
        UUID accountId,
        UUID staffId,
        String role,
        boolean canPracticeAsDoctor,
        List<ManagementDepartment> managementDepartments,
        Instant observedAt) {
    public record ManagementDepartment(
            UUID departmentId, String departmentName, UUID appointmentId, long revision) {}
}
