package com.mediflow.organization.infrastructure.persistence.entity;


import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.DepartmentType;

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

        @Id
    @Column(
            name = "department_id",
            nullable = false,
            updatable = false
    )
    private UUID departmentId;

        @Column(
            name = "department_name",
            nullable = false,
            length = 100
    )
    private String departmentName;

        @Column(
            name = "abbreviation",
            nullable = false,
            length = 20
    )
    private String abbreviation;

        @Enumerated(EnumType.STRING)
    @Column(
            name = "department_type",
            nullable = false,
            length = 20
    )
    private DepartmentType departmentType;

        @Column(name = "department_head_id")
    private UUID departmentHeadId;

        @Column(
            name = "location",
            length = 255
    )
    private String location;

        @Column(
            name = "is_active",
            nullable = false
    )
    private boolean active;

        @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

        @Column(name = "updated_at")
    private Instant updatedAt;

        protected DepartmentEntity() {
    }

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
