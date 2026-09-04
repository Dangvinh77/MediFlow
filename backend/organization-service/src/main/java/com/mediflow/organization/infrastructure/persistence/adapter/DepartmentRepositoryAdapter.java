package com.mediflow.organization.infrastructure.persistence.adapter;


import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;
import com.mediflow.organization.infrastructure.persistence.repository.DepartmentJpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter triển khai DepartmentRepository của Application.
 *
 * Nhiệm vụ:
 * - Nhận yêu cầu từ Application.
 * - Gọi Spring Data JPA.
 * - Mapping Entity ↔ Domain.
 *
 * Application không biết class này tồn tại.
 * Application chỉ biết DepartmentRepository.
 */
@Component
public class DepartmentRepositoryAdapter
        implements DepartmentRepository {

    private final DepartmentJpaRepository jpaRepository;

    /**
     * Constructor Injection.
     */
    public DepartmentRepositoryAdapter(
            DepartmentJpaRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Kiểm tra abbreviation đã tồn tại chưa.
     *
     * Application gọi:
     *
     * departmentRepository.existsByAbbreviation(...)
     *
     * và thực tế method này sẽ được chạy.
     */
    @Override
    public boolean existsByAbbreviation(
            String abbreviation
    ) {
        return jpaRepository.existsByAbbreviation(
                abbreviation
        );
    }

    /**
     * Tìm Department theo ID.
     *
     * JPA trả về DepartmentEntity.
     * Adapter chuyển Entity → Domain.
     */
    @Override
    public Optional<Department> findById(
            UUID departmentId
    ) {
        return jpaRepository
                .findById(departmentId)
                .map(this::toDomain);
    }

    /**
     * Lưu Department.
     *
     * Flow:
     *
     * Domain
     *   ↓
     * Entity
     *   ↓
     * JPA
     *   ↓
     * Database
     *
     * Sau khi save xong:
     *
     * Entity
     *   ↓
     * Domain
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

    /**
     * Mapping:
     *
     * DepartmentEntity → Department
     *
     * Đây là boundary giữa Persistence và Domain.
     */
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

    /**
     * Mapping:
     *
     * Department → DepartmentEntity
     */
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
}