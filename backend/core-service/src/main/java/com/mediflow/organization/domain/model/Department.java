package com.mediflow.organization.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.exception.InvalidDepartmentException;

public class Department {

        private final UUID departmentId;

        private String departmentName;

    /**
     *
     * "XN"
     */
    private String abbreviation;

        private DepartmentType departmentType;

        private UUID departmentHeadId;

        private String location;

        private boolean active;

        private final Instant createdAt;

        private Instant updatedAt;

        private Department(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            UUID departmentHeadId,
            String location,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
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

        public static Department create(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location) {
        validateData(
                departmentId,
                departmentName,
                abbreviation,
                departmentType);

        Instant now = Instant.now();

        return new Department(
                departmentId,
                departmentName,
                abbreviation,
                departmentType,
                null,
                location,
                true,
                now,
                now);
    }

        public static Department reconstitute(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            UUID departmentHeadId,
            String location,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        validateData(
                departmentId,
                departmentName,
                abbreviation,
                departmentType);

        return new Department(
                departmentId,
                departmentName,
                abbreviation,
                departmentType,
                departmentHeadId,
                location,
                active,
                createdAt,
                updatedAt);
    }

    

        public void update(
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location) {
        validateData(
                this.departmentId,
                departmentName,
                abbreviation,
                departmentType);

        this.departmentName = departmentName;
        this.abbreviation = abbreviation;
        this.departmentType = departmentType;
        this.location = location;

        touch();
    }

        public void changeHead(Staff staff) {

        if (staff == null) {
            throw new InvalidDepartmentHeadException(
                    "Department head cannot be null");
        }

        /*
         */
        if (!staff.isActive()) {
            throw new InvalidDepartmentHeadException(
                    "Department head must be active");
        }

        /*
         */
        if (!departmentId.equals(staff.getDepartmentId())) {
            throw new InvalidDepartmentHeadException(
                    "Staff does not belong to this department");
        }

        /*
         *
         */
        if (staff.getJobTitle() != JobTitle.DOCTOR) {
            throw new InvalidDepartmentHeadException(
                    "Department head must be a doctor");
        }

        this.departmentHeadId = staff.getStaffId();

        touch();
    }

        public void removeHead() {
        this.departmentHeadId = null;

        touch();
    }

        public void deactivate(boolean hasActiveStaff) {

        if (hasActiveStaff) {
            throw new DepartmentHasActiveStaffException(
                    departmentId);
        }

        this.active = false;

        touch();
    }

        public void activate() {
        this.active = true;

        touch();
    }

        private void touch() {
        this.updatedAt = Instant.now();
    }

    private static void validateData(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType) {

        if (departmentId == null) {
            throw new InvalidDepartmentException(
                    "Department ID must not be null");
        }

        if (departmentName == null || departmentName.isBlank()) {
            throw new InvalidDepartmentException(
                    "Department name must not be blank");
        }

        if (abbreviation == null || abbreviation.isBlank()) {
            throw new InvalidDepartmentException(
                    "Department abbreviation must not be blank");
        }

        if (abbreviation.length() > 20) {
            throw new InvalidDepartmentException(
                    "Department abbreviation must not exceed 20 characters");
        }

        if (!abbreviation.equals(abbreviation.toUpperCase(Locale.ROOT))) {
            throw new InvalidDepartmentException(
                    "Department abbreviation must be uppercase");
        }

        if (departmentType == null) {
            throw new InvalidDepartmentException(
                    "Department type must not be null");
        }
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
