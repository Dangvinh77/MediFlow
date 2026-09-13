package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.StaffAlreadyInDepartmentException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.Staff;

public class ChangeStaffDepartmentService
        implements ChangeStaffDepartmentUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;

    public ChangeStaffDepartmentService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher) {

        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(
            UUID staffId,
            UUID newDepartmentId) {

        Staff staff = staffRepository
                .findById(staffId)
                .orElseThrow(() ->
                        new StaffNotFoundException(staffId));

        Department newDepartment = departmentRepository
                .findById(newDepartmentId)
                .orElseThrow(() ->
                        new DepartmentNotFoundException(newDepartmentId));

        if (!newDepartment.isActive()) {
            throw new DepartmentInactiveException(newDepartmentId);
        }

        if (staff.getDepartmentId().equals(newDepartmentId)) {
            throw new StaffAlreadyInDepartmentException(newDepartmentId);
        }

        UUID oldDepartmentId = staff.getDepartmentId();

        staff.changeDepartment(newDepartmentId);

        staffRepository.save(staff);

        eventPublisher.publish(
                new StaffDepartmentChangedEvent(
                        staff.getStaffId(),
                        oldDepartmentId,
                        newDepartmentId
                )
        );
    }

    public record StaffDepartmentChangedEvent(
            UUID staffId,
            UUID oldDepartmentId,
            UUID newDepartmentId
    ) {}
}