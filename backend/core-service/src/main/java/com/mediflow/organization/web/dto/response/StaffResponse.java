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
        Instant updatedAt,
        String identityNumber,
        java.time.LocalDate dateOfBirth,
        com.mediflow.organization.domain.model.StaffProfile.Gender gender,
        String address,
        java.time.LocalDate hireDate,
        String qualification) {

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
                staff.getUpdatedAt(),
                staff.getIdentityNumber(),
                staff.getProfile().dateOfBirth(), staff.getProfile().gender(), staff.getProfile().address(), staff.getProfile().hireDate(), staff.getProfile().qualification());
    }
    /** Public work directory: personal and administrative fields are never populated. */
    public static StaffResponse directory(Staff staff) {
        return new StaffResponse(
                staff.getStaffId(),
                staff.getFullName(),
                staff.getDepartmentId(),
                staff.getJobTitle(),
                staff.getSpecialization(),
                null,
                null,
                null,
                staff.isActive(),
                null,
                null,
                null, null, null, null, null, staff.getProfile().qualification());
    }

    public StaffResponse(UUID staffId, String fullName, UUID departmentId, JobTitle jobTitle, String specialization, String licenseNumber, String phoneNumber, String email, boolean active, Instant createdAt, Instant updatedAt, String identityNumber) { this(staffId, fullName, departmentId, jobTitle, specialization, licenseNumber, phoneNumber, email, active, createdAt, updatedAt, identityNumber, null, null, null, null, null); }
}
