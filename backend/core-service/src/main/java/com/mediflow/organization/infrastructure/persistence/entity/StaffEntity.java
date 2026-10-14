package com.mediflow.organization.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.StaffStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "staff")
public class StaffEntity {

        @Id
    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

        @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

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
            Instant updatedAt) {
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
