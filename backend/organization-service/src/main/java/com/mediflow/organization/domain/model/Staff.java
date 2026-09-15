
package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class Staff {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\d{10,15}");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UUID staffId;

    private String fullName;


    private UUID departmentId;

    private JobTitle jobTitle;

    private String specialization;

    private String licenseNumber;

    private String phoneNumber;

    private String email;


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


    // =========================================================

    public static Staff create(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {

        validateData(
                fullName,
                departmentId,
                jobTitle,
                licenseNumber,
                phoneNumber,
                email);

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


    //

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

        validateData(
                fullName,
                departmentId,
                jobTitle,
                licenseNumber,
                phoneNumber,
                email);

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

    // =========================================================

    public void update(
            String fullName,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {

        validateData(
                fullName,
                this.departmentId,
                jobTitle,
                licenseNumber,
                phoneNumber,
                email);

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


    // =========================================================

    public void activate() {

        this.active = true;
        this.updatedAt = Instant.now();
    }

    // =========================================================


    // =========================================================

    public void deactivate() {

        this.active = false;
        this.updatedAt = Instant.now();
    }

    // =========================================================

    //

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

    private static void validateData(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String licenseNumber,
            String phoneNumber,
            String email) {

        if (fullName == null || fullName.isBlank()) {
            throw new InvalidStaffDataException(
                    "Staff full name must not be blank");
        }

        if (departmentId == null) {
            throw new InvalidStaffDataException(
                    "Staff department ID must not be null");
        }

        if (jobTitle == null) {
            throw new InvalidStaffDataException(
                    "Staff job title must not be null");
        }

        if (phoneNumber != null
                && !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new InvalidStaffDataException(
                    "Phone number must contain 10 to 15 digits");
        }

        if (email != null
                && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidStaffDataException(
                    "Email must be valid");
        }

        validateLicense(jobTitle, licenseNumber);
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
