package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

import java.time.Instant;
import java.util.UUID;

public record StaffResponse(
        UUID staffId,
        String fullName,
        UUID departmentId,
        JobTitle jobTitle,
        String specialization,
        String licenseNumber,
        String phoneNumber,
        String email,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static StaffResponse from(Staff staff) {
        return new StaffResponse(
                staff.getStaffId(),
                staff.getFullName(),
                staff.getDepartmentId(),
                staff.getJobTitle(),
                staff.getSpecialization(),
                staff.getLicenseNumber(),
                staff.getPhoneNumber(),
                staff.getEmail(),
                staff.isActive(),
                staff.getCreatedAt(),
                staff.getUpdatedAt());
    }
}
