package com.mediflow.organization.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.StaffStatus;

import jakarta.persistence.*;

/**
 * Persistence Entity đại diện cho bảng STAFF.
 *
 * LƯU Ý:
 * - Đây không phải Domain Model.
 * - Class này chỉ phục vụ JPA/Persistence.
 * - Không đặt business logic vào Entity.
 * - departmentId chỉ lưu UUID, không tạo @ManyToOne với Department.
 */
@Entity
@Table(name = "staff")
public class StaffEntity {

    /**
     * Primary Key.
     */
    @Id
    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    /**
     * Họ và tên nhân viên.
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * Department mà Staff đang thuộc về.
     *
     * Chỉ lưu UUID.
     */
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    /**
     * Chức danh:
     *
     * DOCTOR
     * NURSE
     * TECHNICIAN
     * PHARMACIST
     * CASHIER
     * MANAGER
     * ADMINISTRATIVE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_title", nullable = false, length = 30)
    private JobTitle jobTitle;

    /**
     * Chuyên môn của Staff.
     *
     * Có thể null.
     */
    @Column(name = "specialization", length = 100)
    private String specialization;

    /**
     * Số giấy phép hành nghề.
     *
     * Có thể null đối với các chức danh không yêu cầu.
     *
     * Rule DOCTOR bắt buộc licenseNumber
     * được xử lý ở Domain.
     */
    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    /**
     * Số điện thoại.
     */
    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;

    /**
     * Email.
     */
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    /**
     * Trạng thái Staff.
     *
     * ACTIVE
     * INACTIVE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StaffStatus status;

    /**
     * Thời điểm tạo Staff.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Thời điểm cập nhật gần nhất.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Constructor rỗng bắt buộc cho JPA.
     */
    protected StaffEntity() {
    }

    /**
     * Constructor dùng khi mapping Domain → Entity.
     */
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