package com.mediflow.organization.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;
import com.mediflow.organization.infrastructure.persistence.repository.StaffJpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter kết nối Application Port với Spring Data JPA.
 *
 * Nhiệm vụ:
 *
 * Application
 * ↓
 * StaffRepository
 * ↓
 * StaffRepositoryAdapter
 * ↓
 * StaffJpaRepository
 * ↓
 * PostgreSQL
 *
 * Đồng thời chịu trách nhiệm mapping:
 *
 * Domain ↔ Entity
 */
@Component
public class StaffRepositoryAdapter
        implements StaffRepository {

    private final StaffJpaRepository jpaRepository;

    /**
     * Constructor Injection.
     */
    public StaffRepositoryAdapter(
            StaffJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Kiểm tra Department có Staff ACTIVE hay không.
     *
     * Application gọi:
     *
     * staffRepository
     * .existsByDepartmentIdAndActiveTrue(departmentId);
     *
     * Adapter chuyển yêu cầu đó thành query JPA.
     */
    @Override
    public boolean existsByDepartmentIdAndActiveTrue(
            UUID departmentId) {
        return jpaRepository.existsByDepartmentIdAndStatus(
                departmentId,
                StaffStatus.ACTIVE);
    }

    /**
     * Tìm Staff theo ID.
     *
     * JPA trả về StaffEntity.
     * Adapter mapping Entity → Domain.
     */
    @Override
    public Optional<Staff> findById(
            UUID staffId) {
        return jpaRepository
                .findById(staffId)
                .map(this::toDomain);
    }

    /**
     * Lưu Staff.
     *
     * Flow:
     *
     * Domain
     * ↓
     * Entity
     * ↓
     * JPA
     * ↓
     * Database
     *
     * Sau khi save:
     *
     * Entity
     * ↓
     * Domain
     */
    @Override
    public Staff save(
            Staff staff) {

        StaffEntity entity = toEntity(staff);

        StaffEntity savedEntity = jpaRepository.save(entity);

        return toDomain(savedEntity);
    }

    /**
     * Mapping:
     *
     * StaffEntity → Staff
     *
     * Dùng khi đọc dữ liệu từ database.
     */
    private Staff toDomain(StaffEntity entity) {

        return Staff.reconstitute(
                entity.getStaffId(),
                entity.getFullName(),
                entity.getDepartmentId(),
                entity.getJobTitle(),
                entity.getSpecialization(),
                entity.getLicenseNumber(),
                entity.getPhoneNumber(),
                entity.getEmail(),

                // DB: ACTIVE / INACTIVE
                // Domain: true / false
                entity.getStatus() == StaffStatus.ACTIVE,

                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * Mapping:
     *
     * Staff → StaffEntity
     *
     * Dùng trước khi ghi dữ liệu vào database.
     */
    private StaffEntity toEntity(Staff staff) {

        return new StaffEntity(
                staff.getStaffId(),
                staff.getFullName(),
                staff.getDepartmentId(),
                staff.getJobTitle(),
                staff.getSpecialization(),
                staff.getLicenseNumber(),
                staff.getPhoneNumber(),
                staff.getEmail(),

                // Domain: true / false
                // DB: ACTIVE / INACTIVE
                staff.isActive()
                        ? StaffStatus.ACTIVE
                        : StaffStatus.INACTIVE,

                staff.getCreatedAt(),
                staff.getUpdatedAt());
    }
}