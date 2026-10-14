package com.mediflow.organization.infrastructure.persistence.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;

import java.util.UUID;

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
}
