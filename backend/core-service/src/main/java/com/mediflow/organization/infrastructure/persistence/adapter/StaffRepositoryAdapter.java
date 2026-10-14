package com.mediflow.organization.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;
import com.mediflow.organization.infrastructure.persistence.repository.StaffJpaRepository;

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
    public PageResult<Staff> search(
            UUID departmentId,
            JobTitle jobTitle,
            PageQuery pageQuery) {
        PageRequest pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<StaffEntity> page;
        if (departmentId != null && jobTitle != null) {
            page = jpaRepository.findByDepartmentIdAndJobTitle(
                    departmentId, jobTitle, pageable);
        } else if (departmentId != null) {
            page = jpaRepository.findByDepartmentId(departmentId, pageable);
        } else if (jobTitle != null) {
            page = jpaRepository.findByJobTitle(jobTitle, pageable);
        } else {
            page = jpaRepository.findAll(pageable);
        }

        return PageResult.of(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
