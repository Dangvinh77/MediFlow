package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.JobTitle;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateStaffRequest {

    @NotBlank(message = "Full name must not be blank")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @NotNull(message = "Job title must not be null")
    private JobTitle jobTitle;

    @Size(max = 100, message = "Specialization must not exceed 100 characters")
    private String specialization;

    @Size(max = 50, message = "License number must not exceed 50 characters")
    private String licenseNumber;

    @Pattern(
            regexp = "\\d{10,15}",
            message = "Phone number must contain 10 to 15 digits"
    )
    private String phoneNumber;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    public UpdateStaffRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public JobTitle getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(JobTitle jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}