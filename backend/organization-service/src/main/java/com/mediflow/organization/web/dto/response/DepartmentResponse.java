package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
        UUID departmentId,
        String departmentName,
        String abbreviation,
        DepartmentType departmentType,
        UUID departmentHeadId,
        String location,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static DepartmentResponse from(Department domain) {
        return new DepartmentResponse(
                domain.getDepartmentId(),
                domain.getDepartmentName(),
                domain.getAbbreviation(),
                domain.getDepartmentType(),
                domain.getDepartmentHeadId(),
                domain.getLocation(),
                domain.isActive(),
                domain.getCreatedAt(),
                domain.getUpdatedAt());
    }
}
