package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.organization.domain.model.DepartmentProfile;

import java.util.UUID;

@Transactional("organizationTransactionManager")
public class UpdateDepartmentService implements UpdateDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;

    public UpdateDepartmentService(
            DepartmentRepository departmentRepository,
            StaffRepository staffRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.staffRepository = staffRepository;
    }

    @Override
    public Department execute(
            UUID departmentId,
            String departmentName,
            DepartmentType departmentType,
            String location,
            UUID departmentHeadId,
            Boolean active
    ) {
        return execute(departmentId, departmentName, departmentType, location, departmentHeadId, active, null);
    }

    @Override
    public Department execute(UUID departmentId, String departmentName, DepartmentType departmentType, String location, UUID departmentHeadId, Boolean active, DepartmentProfile profile) {
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        department.update(
                departmentName,
                department.getAbbreviation(),
                departmentType,
                location
        );

        if (departmentHeadId == null) {
            department.removeHead();
        } else {
            Staff head = staffRepository.findById(departmentHeadId)
                    .orElseThrow(() -> new StaffNotFoundException(departmentHeadId));
            department.changeHead(head);
        }

        if (active != null && active != department.isActive()) {
            if (active) {
                department.activate();
            } else {
                department.deactivate(
                        staffRepository.existsByDepartmentIdAndActiveTrue(departmentId));
            }
        }

        if (profile != null) department.withProfile(profile);
        return departmentRepository.save(department);
    }
}
