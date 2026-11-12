package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentProfile;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.application.port.out.RoomRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Transactional("organizationTransactionManager")
public class UpdateDepartmentService implements UpdateDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;
    private final RoomRepository roomRepository;

    public UpdateDepartmentService(
            DepartmentRepository departmentRepository, StaffRepository staffRepository,
            RoomRepository roomRepository) {
        this.departmentRepository = departmentRepository;
        this.staffRepository = staffRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    public Department execute(
            UUID departmentId,
            String departmentName,
            DepartmentType departmentType,
            String location,
            UUID departmentHeadId,
            Boolean active) {
        return execute(
                departmentId,
                departmentName,
                departmentType,
                location,
                departmentHeadId,
                active,
                null);
    }

    @Override
    public Department execute(
            UUID departmentId,
            String departmentName,
            DepartmentType departmentType,
            String location,
            UUID departmentHeadId,
            Boolean active,
            DepartmentProfile profile) {
        Department department =
                departmentRepository
                        .findByIdForUpdate(departmentId)
                        .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        if (!Objects.equals(departmentHeadId, department.getDepartmentHeadId())) {
            throw new BusinessRuleException(
                    "HEAD_APPOINTMENT_REQUIRED",
                    "Use the department head appointment command to change or revoke the head");
        }

        // Room types belong to one category: a unit with rooms keeps its category.
        if (departmentType != null && departmentType != department.getDepartmentType()
                && !roomRepository.search(departmentId, null, false).isEmpty()) {
            throw new BusinessRuleException(
                    "DEPARTMENT_TYPE_HAS_ROOMS",
                    "A department with workplace rooms cannot change its category");
        }

        // The location follows the office room; it is never typed here.
        department.update(departmentName, department.getAbbreviation(), departmentType, department.getLocation());

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
