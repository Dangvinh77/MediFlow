package com.mediflow.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;

/**
 * Domain model đại diện cho nhân viên trong bệnh viện.
 *
 * Staff có thể là:
 *
 * - Doctor
 * - Nurse
 * - Technician
 * - Pharmacist
 * - Cashier
 * - Manager
 * - Administrative
 *
 * Staff thuộc đúng một Department thông qua departmentId.
 *
 * Quan trọng:
 * Staff chỉ giữ UUID của Department,
 * không giữ trực tiếp Department object.
 */
public class Staff {

    /**
     * ID duy nhất của nhân viên.
     *
     * Các service nghiệp vụ khác sẽ tham chiếu đến ID này.
     *
     * Ví dụ:
     *
     * Appointment.staffId
     * Prescription.staffId
     * MedicalRecord.staffId
     */
    private final UUID staffId;

    /**
     * Họ và tên nhân viên.
     */
    private String fullName;

    /**
     * ID khoa mà nhân viên đang thuộc về.
     *
     * Một Staff phải thuộc đúng một Department.
     */
    private UUID departmentId;

    /**
     * Chức danh nghề nghiệp.
     */
    private JobTitle jobTitle;

    /**
     * Chuyên khoa sâu.
     *
     * Có thể null với những chức danh không cần specialization.
     */
    private String specialization;

    /**
     * Số chứng chỉ hành nghề.
     *
     * Doctor bắt buộc phải có licenseNumber.
     */
    private String licenseNumber;

    /**
     * Số điện thoại nhân viên.
     */
    private String phoneNumber;

    /**
     * Email nhân viên.
     */
    private String email;

    /**
     * Trạng thái làm việc của nhân viên.
     *
     * true  = đang làm việc
     * false = đã nghỉ / không hoạt động
     */
    private boolean active;

    /**
     * Thời điểm tạo Staff.
     */
    private final Instant createdAt;

    /**
     * Thời điểm cập nhật Staff gần nhất.
     */
    private Instant updatedAt;

    /**
     * Constructor dùng để khôi phục Staff từ persistence.
     *
     * Constructor này cũng kiểm tra domain invariant:
     * Doctor bắt buộc phải có license number.
     */
    public Staff(
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
            Instant updatedAt
    ) {
        validateLicense(jobTitle, licenseNumber);

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

    /**
     * Factory method dùng để tạo Staff mới.
     *
     * Staff mới mặc định ACTIVE.
     */
    public static Staff create(
            UUID staffId,
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    ) {
        Instant now = Instant.now();

        return new Staff(
                staffId,
                fullName,
                departmentId,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email,
                true,
                now,
                now
        );
    }

    /**
     * Thay đổi khoa làm việc của Staff.
     *
     * Ví dụ:
     *
     *     Nội -> Ngoại
     *
     * Method này chỉ thay đổi state của Staff.
     *
     * Việc publish event:
     *
     *     staff.department.changed
     *
     * sẽ do Application layer đảm nhiệm.
     */
    public void changeDepartment(UUID newDepartmentId) {

        if (newDepartmentId == null) {
            throw new IllegalArgumentException(
                    "Department ID cannot be null"
            );
        }

        this.departmentId = newDepartmentId;

        touch();
    }

    /**
     * Cập nhật thông tin nhân viên.
     *
     * Nếu jobTitle thay đổi thành DOCTOR,
     * licenseNumber bắt buộc phải tồn tại.
     */
    public void update(
            String fullName,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    ) {
        validateLicense(jobTitle, licenseNumber);

        this.fullName = fullName;
        this.jobTitle = jobTitle;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.phoneNumber = phoneNumber;
        this.email = email;

        touch();
    }

    /**
     * Kích hoạt Staff.
     */
    public void activate() {
        this.active = true;

        touch();
    }

    /**
     * Vô hiệu hóa Staff.
     */
    public void deactivate() {
        this.active = false;

        touch();
    }

    /**
     * Business rule:
     *
     * Nếu Staff là DOCTOR thì bắt buộc phải có
     * số chứng chỉ hành nghề.
     *
     * Đây là domain invariant nên phải được enforce
     * ngay trong domain, không chỉ dựa vào @NotBlank của DTO.
     */
    private void validateLicense(
            JobTitle jobTitle,
            String licenseNumber
    ) {
        if (jobTitle == JobTitle.DOCTOR
                && (licenseNumber == null || licenseNumber.isBlank())) {

            throw new DoctorLicenseRequiredException();
        }
    }

    /**
     * Cập nhật thời điểm modified.
     */
    private void touch() {
        this.updatedAt = Instant.now();
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
