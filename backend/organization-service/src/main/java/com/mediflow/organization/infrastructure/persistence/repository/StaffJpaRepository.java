package com.mediflow.organization.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;

import java.util.UUID;

/**
 * Spring Data JPA Repository cho Staff.
 *
 * Repository này làm việc trực tiếp với database.
 */
public interface StaffJpaRepository
        extends JpaRepository<StaffEntity, UUID> {

    /**
     * Kiểm tra Department có Staff đang ACTIVE hay không.
     *
     * Dùng cho business rule:
     *
     * "Không được deactivate Department
     *  nếu Department vẫn còn active staff."
     *
     * Spring Data JPA tự tạo query từ method name.
     */
    boolean existsByDepartmentIdAndStatus(
            UUID departmentId,
            StaffStatus status
    );
}