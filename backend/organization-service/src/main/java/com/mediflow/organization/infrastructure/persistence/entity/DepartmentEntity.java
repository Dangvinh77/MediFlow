package com.mediflow.organization.infrastructure.persistence.entity;


import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.DepartmentType;

/**
 * Persistence Entity đại diện cho bảng DEPARTMENT trong database.
 *
 * LƯU Ý:
 * - Đây KHÔNG phải Domain Model.
 * - Class này chỉ phục vụ JPA/Persistence.
 * - Không đặt business logic vào Entity này.
 */
@Entity
@Table(
        name = "department",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_department_abbreviation",
                        columnNames = "abbreviation"
                )
        }
)
public class DepartmentEntity {

    /**
     * Primary Key.
     */
    @Id
    @Column(
            name = "department_id",
            nullable = false,
            updatable = false
    )
    private UUID departmentId;

    /**
     * Tên Department.
     */
    @Column(
            name = "department_name",
            nullable = false,
            length = 100
    )
    private String departmentName;

    /**
     * Viết tắt Department.
     *
     * Database có UNIQUE constraint.
     */
    @Column(
            name = "abbreviation",
            nullable = false,
            length = 20,
            unique = true
    )
    private String abbreviation;

    /**
     * Loại Department:
     *
     * CLINICAL
     * PARACLINICAL
     * ADMINISTRATIVE
     *
     * Lưu dưới dạng String trong DB,
     * không lưu ordinal 0, 1, 2.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "department_type",
            nullable = false,
            length = 20
    )
    private DepartmentType departmentType;

    /**
     * ID của Staff đang làm trưởng Department.
     *
     * Có thể null.
     *
     * Không tạo JPA relationship tới Staff vì Staff
     * cũng thuộc cùng bounded context nhưng ta đang
     * giữ Domain model đơn giản bằng UUID.
     */
    @Column(name = "department_head_id")
    private UUID departmentHeadId;

    /**
     * Địa điểm Department.
     */
    @Column(
            name = "location",
            length = 255
    )
    private String location;

    /**
     * Trạng thái hoạt động của Department.
     */
    @Column(
            name = "is_active",
            nullable = false
    )
    private boolean active;

    /**
     * Thời điểm tạo.
     */
    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    /**
     * Thời điểm cập nhật gần nhất.
     */
    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    /**
     * Constructor rỗng bắt buộc cho JPA.
     */
    protected DepartmentEntity() {
    }

    /**
     * Constructor đầy đủ dùng khi mapping Domain → Entity.
     */
    public DepartmentEntity(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            UUID departmentHeadId,
            String location,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.abbreviation = abbreviation;
        this.departmentType = departmentType;
        this.departmentHeadId = departmentHeadId;
        this.location = location;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public DepartmentType getDepartmentType() {
        return departmentType;
    }

    public UUID getDepartmentHeadId() {
        return departmentHeadId;
    }

    public String getLocation() {
        return location;
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