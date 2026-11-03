package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.JobTitle;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStaffRequest(
        @NotBlank(message = "Full name must not be blank")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,
        @NotNull(message = "Job title must not be null")
        JobTitle jobTitle,
        @Size(max = 100, message = "Specialization must not exceed 100 characters")
        String specialization,
        @Size(max = 50, message = "License number must not exceed 50 characters")
        String licenseNumber,
        @Pattern(regexp = "\\d{10,15}", message = "Phone number must contain 10 to 15 digits")
        String phoneNumber,
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,
        @jakarta.validation.Valid StaffProfileRequest profile,
        @Pattern(regexp = "\\d{12}", message = "Identity number must contain exactly 12 digits") String identityNumber,
        @Size(max = 500, message = "Identity change reason must not exceed 500 characters") String identityChangeReason) {
    public UpdateStaffRequest(String fullName, JobTitle jobTitle, String specialization, String licenseNumber, String phoneNumber, String email) {
        this(fullName, jobTitle, specialization, licenseNumber, phoneNumber, email, null, null, null);
    }
    public UpdateStaffRequest(String fullName, JobTitle jobTitle, String specialization, String licenseNumber,
            String phoneNumber, String email, StaffProfileRequest profile) {
        this(fullName, jobTitle, specialization, licenseNumber, phoneNumber, email, profile, null, null);
    }
}
