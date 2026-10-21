package com.mediflow.organization.infrastructure.persistence.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentJpaRepository
        extends JpaRepository<DepartmentEntity, UUID> {

    /**
     *
     *
     *
     *     SELECT 1
     *     FROM department
     * );
     */
    boolean existsByAbbreviation(String abbreviation);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DepartmentEntity d where d.departmentId = :id")
    Optional<DepartmentEntity> findByIdForUpdate(@Param("id") UUID id);
}
