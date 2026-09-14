package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    
    private UUID staffId;
    private String fullName;
    private UUID departmentId;
    private JobTitle jobTitle;
    private String specialization;
    private String licenseNumber;
    private String phoneNumber;
    private String email;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static StaffResponse from(Staff staff) {
    return StaffResponse.builder()
            .staffId(staff.getStaffId())
            .fullName(staff.getFullName())
            .departmentId(staff.getDepartmentId())
            .jobTitle(staff.getJobTitle())
            .specialization(staff.getSpecialization())
            .licenseNumber(staff.getLicenseNumber())
            .phoneNumber(staff.getPhoneNumber())
            .email(staff.getEmail())
            .active(staff.isActive())
            .createdAt(staff.getCreatedAt())
            .updatedAt(staff.getUpdatedAt())
            .build();
}
}