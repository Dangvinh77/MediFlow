package com.mediflow.organization.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffJpaRepository
        extends JpaRepository<StaffEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffEntity s where s.staffId = :id")
    Optional<StaffEntity> findByIdForUpdate(@Param("id") UUID id);


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
