package com.mediflow.organization.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;

import java.util.List;
import java.util.UUID;

public interface StaffJpaRepository
        extends JpaRepository<StaffEntity, UUID> {

        boolean existsByDepartmentIdAndStatus(
            UUID departmentId,
            StaffStatus status
    );
    List<StaffEntity> findByDepartmentId(UUID departmentId);
}
