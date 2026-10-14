package com.mediflow.organization.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;

import java.util.UUID;

public interface StaffJpaRepository
        extends JpaRepository<StaffEntity, UUID> {

        boolean existsByDepartmentIdAndStatus(
            UUID departmentId,
            StaffStatus status
    );
    Page<StaffEntity> findByDepartmentId(UUID departmentId, Pageable pageable);

    Page<StaffEntity> findByJobTitle(
            JobTitle jobTitle,
            Pageable pageable);

    Page<StaffEntity> findByDepartmentIdAndJobTitle(
            UUID departmentId,
            JobTitle jobTitle,
            Pageable pageable);
}
