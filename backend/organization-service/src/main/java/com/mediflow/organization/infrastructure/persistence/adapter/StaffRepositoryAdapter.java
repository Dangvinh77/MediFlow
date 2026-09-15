package com.mediflow.organization.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;
import com.mediflow.organization.infrastructure.persistence.repository.StaffJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 *
 *
 * ↓
 * ↓
 * ↓
 * ↓
 * PostgreSQL
 *
 *
 */
@Component
public class StaffRepositoryAdapter
        implements StaffRepository {

    private final StaffJpaRepository jpaRepository;

        public StaffRepositoryAdapter(
            StaffJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

        @Override
    public boolean existsByDepartmentIdAndActiveTrue(
            UUID departmentId) {
        return jpaRepository.existsByDepartmentIdAndStatus(
                departmentId,
                StaffStatus.ACTIVE);
    }

        @Override
    public Optional<Staff> findById(
            UUID staffId) {
        return jpaRepository
                .findById(staffId)
                .map(this::toDomain);
    }

    /**
     *
     * Flow:
     *
     * ↓
     * ↓
     * JPA
     * ↓
     * Database
     *
     *
     * ↓
     */
    @Override
    public Staff save(
            Staff staff) {

        StaffEntity entity = toEntity(staff);

        StaffEntity savedEntity = jpaRepository.save(entity);

        return toDomain(savedEntity);
    }

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



                entity.getStatus() == StaffStatus.ACTIVE,

                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

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



                staff.isActive()
                        ? StaffStatus.ACTIVE
                        : StaffStatus.INACTIVE,

                staff.getCreatedAt(),
                staff.getUpdatedAt());
    }

    @Override
    public List<Staff> findByDepartmentId(UUID departmentId) {
        
        return jpaRepository.findByDepartmentId(departmentId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Staff> findAll() {
        
        return jpaRepository.findAll().stream()
            .map(this::toDomain)
            .toList();
    }
}
