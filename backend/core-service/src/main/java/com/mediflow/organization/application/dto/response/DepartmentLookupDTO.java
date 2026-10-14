package com.mediflow.organization.application.dto.response;

import java.util.UUID;

/** Additive department identity projection for service-to-service lookups. */
public record DepartmentLookupDTO(
        boolean exists,
        boolean active,
        UUID departmentId,
        String departmentName,
        String departmentType) {

    public static DepartmentLookupDTO missing(UUID departmentId) {
        return new DepartmentLookupDTO(false, false, departmentId, null, null);
    }
}
