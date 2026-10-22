package com.mediflow.organization.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.StaffStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "staff", uniqueConstraints = @UniqueConstraint(name = "uk_staff_identity_number", columnNames = "identity_number"))
public class StaffEntity {
    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private com.mediflow.organization.domain.model.StaffProfile.Gender gender;
    @Column(name = "address", length = 255)
    private String address;
    @Column(name = "hire_date")
    private java.time.LocalDate hireDate;
    @Column(name = "qualification", length = 100)
    private String qualification;
    public com.mediflow.organization.domain.model.StaffProfile getProfile() { return new com.mediflow.organization.domain.model.StaffProfile(dateOfBirth, gender, address, hireDate, qualification); }
    public StaffEntity withProfile(com.mediflow.organization.domain.model.StaffProfile profile) {
        this.dateOfBirth = profile.dateOfBirth();
        this.gender = profile.gender();
        this.address = profile.address();
        this.hireDate = profile.hireDate();
        this.qualification = profile.qualification();
        return this;
    }


        @Id
    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

        @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "identity_number", length = 12, updatable = false)
    private String identityNumber;

        @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    /**
     *
     * DOCTOR
     * NURSE
     * MANAGER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_title", nullable = false, length = 20)
    private JobTitle jobTitle;

        @Column(name = "specialization", length = 100)
    private String specialization;

        @Column(name = "license_number", length = 50)
    private String licenseNumber;

        @Column(name = "phone_number", length = 15)
    private String phoneNumber;

        @Column(name = "email", length = 100)
    private String email;

        @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StaffStatus status;

        @Column(name = "created_at", nullable = false)
    private Instant createdAt;

        @Column(name = "updated_at")
    private Instant updatedAt;

        protected StaffEntity() {
    }

        public StaffEntity(
            UUID staffId,
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email,
            StaffStatus status,
            Instant createdAt,
            Instant updatedAt,
            String identityNumber) {
        this.identityNumber = identityNumber;
        this.staffId = staffId;
        this.fullName = fullName;
        this.departmentId = departmentId;
        this.jobTitle = jobTitle;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public String getFullName() {
        return fullName;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public JobTitle getJobTitle() {
        return jobTitle;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public StaffStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
