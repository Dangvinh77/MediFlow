
package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;

import java.time.Instant;
import java.util.UUID;

public class Staff {

    private final UUID staffId;

    private String fullName;

    // Chỉ lưu ID của Department
    private UUID departmentId;

    private JobTitle jobTitle;

    private String specialization;

    private String licenseNumber;

    private String phoneNumber;

    private String email;

    // Domain sử dụng boolean
    private boolean active;

    private final Instant createdAt;

    private Instant updatedAt;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    private Staff(
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
        this.staffId = staffId;
        this.fullName = fullName;
        this.departmentId = departmentId;
        this.jobTitle = jobTitle;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // =========================================================
    // CREATE
    // Tạo Staff mới.
    // Staff mới mặc định ACTIVE.
    // =========================================================

    public static Staff create(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {

        validateLicense(jobTitle, licenseNumber);

        Instant now = Instant.now();

        return new Staff(
                UUID.randomUUID(),
                fullName,
                departmentId,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email,
                true,
                now,
                now);
    }

    // =========================================================
    // RECONSTITUTE
    // Dùng khi đọc Staff từ Database.
    //
    // Không dùng create() vì Staff đã tồn tại trong DB.
    // =========================================================

    public static Staff reconstitute(
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

        validateLicense(jobTitle, licenseNumber);

        return new Staff(
                staffId,
                fullName,
                departmentId,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email,
                active,
                createdAt,
                updatedAt);
    }

    // =========================================================
    // UPDATE
    // Cập nhật thông tin Staff.
    // =========================================================

    public void update(
            String fullName,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {

        validateLicense(jobTitle, licenseNumber);

        this.fullName = fullName;
        this.jobTitle = jobTitle;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.phoneNumber = phoneNumber;
        this.email = email;

        this.updatedAt = Instant.now();
    }

    // =========================================================
    // CHANGE DEPARTMENT
    // Chuyển Staff sang Department khác.
    // =========================================================

    public void changeDepartment(UUID newDepartmentId) {

        if (newDepartmentId == null) {
            throw new IllegalArgumentException(
                    "New department ID must not be null");
        }

        if (newDepartmentId.equals(this.departmentId)) {
            throw new IllegalArgumentException(
                    "Staff is already in this department");
        }

        this.departmentId = newDepartmentId;
        this.updatedAt = Instant.now();
    }

    // =========================================================
    // ACTIVATE
    // Kích hoạt Staff.
    // =========================================================

    public void activate() {

        this.active = true;
        this.updatedAt = Instant.now();
    }

    // =========================================================
    // DEACTIVATE
    // Vô hiệu hóa Staff.
    // =========================================================

    public void deactivate() {

        this.active = false;
        this.updatedAt = Instant.now();
    }

    // =========================================================
    // VALIDATE LICENSE
    //
    // Doctor bắt buộc phải có license number.
    // =========================================================

    private static void validateLicense(
            JobTitle jobTitle,
            String licenseNumber) {

        if (jobTitle == null) {
            throw new IllegalArgumentException(
                    "Job title must not be null");
        }

        if (jobTitle == JobTitle.DOCTOR
                && (licenseNumber == null
                        || licenseNumber.isBlank())) {

            throw new DoctorLicenseRequiredException();
        }
    }

    // =========================================================
    // GETTERS
    // =========================================================

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

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
