package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

import java.util.UUID;

public class UpdateDepartmentService implements UpdateDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;

    public UpdateDepartmentService(
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher
    ) {
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Department execute(
            UUID departmentId,
            String departmentName,
            DepartmentType departmentType,
            String location
    ) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        department.update(
                departmentName,
                department.getAbbreviation(),
                departmentType,
                location
        );

        Department updatedDepartment =
                departmentRepository.save(department);

        eventPublisher.publish(
                new DepartmentUpdatedEvent(
                        updatedDepartment.getDepartmentId(),
                        updatedDepartment.getDepartmentName(),
                        updatedDepartment.getDepartmentType().name()
                )
        );

        return updatedDepartment;
    }

    public record DepartmentUpdatedEvent(
            UUID departmentId,
            String departmentName,
            String departmentType
    ) {
    }
}