package com.mediflow.organization.infrastructure.persistence.adapter;


import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;
import com.mediflow.organization.infrastructure.persistence.repository.DepartmentJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DepartmentRepositoryAdapter
        implements DepartmentRepository {

    private final DepartmentJpaRepository jpaRepository;

        public DepartmentRepositoryAdapter(
            DepartmentJpaRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

        @Override
    public boolean existsByAbbreviation(
            String abbreviation
    ) {
        return jpaRepository.existsByAbbreviation(
                abbreviation
        );
    }

        @Override
    public Optional<Department> findById(
            UUID departmentId
    ) {
        return jpaRepository
                .findById(departmentId)
                .map(this::toDomain);
    }

    /**
     *
     * Flow:
     *
     *   ↓
     *   ↓
     * JPA
     *   ↓
     * Database
     *
     *
     *   ↓
     */
    @Override
    public Department save(
            Department department
    ) {

        DepartmentEntity entity = toEntity(department);

        DepartmentEntity savedEntity =
                jpaRepository.save(entity);

        return toDomain(savedEntity);
    }

        private Department toDomain(
            DepartmentEntity entity
    ) {

        return Department.reconstitute(
                entity.getDepartmentId(),
                entity.getDepartmentName(),
                entity.getAbbreviation(),
                entity.getDepartmentType(),
                entity.getDepartmentHeadId(),
                entity.getLocation(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

        private DepartmentEntity toEntity(
            Department department
    ) {

        return new DepartmentEntity(
                department.getDepartmentId(),
                department.getDepartmentName(),
                department.getAbbreviation(),
                department.getDepartmentType(),
                department.getDepartmentHeadId(),
                department.getLocation(),
                department.isActive(),
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }

    @Override
    public List<Department> findAll() {
        return jpaRepository.findAll().stream()
            .map(this::toDomain)
            .toList();
    }
}
