package com.mediflow.organization.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.organization.domain.model.Staff;

/**
 * Output Port cho việc persistence Staff.
 */
public interface StaffRepository {

    /**
     * Kiểm tra Department có Staff đang ACTIVE hay không.
     *
     * Dùng khi deactivate Department.
     */
    boolean existsByDepartmentIdAndActiveTrue(UUID departmentId);

    /**
     * Tìm Staff theo ID.
     */
    Optional<Staff> findById(UUID staffId);

    /**
     * Lưu Staff.
     */
    Staff save(Staff staff);
}
