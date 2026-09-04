package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.JobTitle;

/**
 * Input Port cho use case tạo Staff.
 */
public interface CreateStaffUseCase {

    /**
     * Tạo Staff mới.
     *
     * Staff bắt buộc thuộc một Department đang active.
     *
     * @return ID của Staff vừa tạo
     */
    UUID execute(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    );
}
