package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Organization-owned phase-1 department lookup wire shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DepartmentLookupResponse(
        Boolean exists,
        Boolean active,
        UUID departmentId,
        String departmentName,
        String departmentType) {
}
