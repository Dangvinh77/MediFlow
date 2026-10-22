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
        Instant updatedAt,
        String description,
        String phoneNumber,
        String email) {

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
                domain.getUpdatedAt(),
                domain.getProfile().description(), domain.getProfile().phoneNumber(), domain.getProfile().email());
    }
    public DepartmentResponse(UUID departmentId, String departmentName, String abbreviation, DepartmentType departmentType, UUID departmentHeadId, String location, boolean active, Instant createdAt, Instant updatedAt) { this(departmentId, departmentName, abbreviation, departmentType, departmentHeadId, location, active, createdAt, updatedAt, null, null, null); }
}
