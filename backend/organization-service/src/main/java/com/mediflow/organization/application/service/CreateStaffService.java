package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

public class CreateStaffService implements CreateStaffUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;

    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher) {

        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID execute(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {

        Department department = departmentRepository
                .findById(departmentId)
                .orElseThrow(() ->
                        new DepartmentNotFoundException(departmentId));

        if (!department.isActive()) {
            throw new DepartmentInactiveException(departmentId);
        }

        Staff staff = Staff.create(
                fullName,
                departmentId,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email
        );

        staffRepository.save(staff);

        eventPublisher.publish(
                new StaffCreatedEvent(
                        staff.getStaffId(),
                        staff.getFullName(),
                        staff.getDepartmentId(),
                        staff.getJobTitle().name()
                )
        );

        return staff.getStaffId();
    }

    public record StaffCreatedEvent(
            UUID staffId,
            String fullName,
            UUID departmentId,
            String jobTitle
    ) {}
}