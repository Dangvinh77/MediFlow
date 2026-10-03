package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.DepartmentLookupDTO;
import com.mediflow.organization.application.port.in.LookupDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.model.Department;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Implements the additive department identity projection. */
@Transactional(readOnly = true)
public class LookupDepartmentService implements LookupDepartmentUseCase {

    private final DepartmentRepository departmentRepository;

    public LookupDepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public DepartmentLookupDTO lookup(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .map(this::toDto)
                .orElseGet(() -> DepartmentLookupDTO.missing(departmentId));
    }

    private DepartmentLookupDTO toDto(Department department) {
        return new DepartmentLookupDTO(
                true,
                department.isActive(),
                department.getDepartmentId(),
                department.getDepartmentName(),
                department.getDepartmentType().name());
    }
}
