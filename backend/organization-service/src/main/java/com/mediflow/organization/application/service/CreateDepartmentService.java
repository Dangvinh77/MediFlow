package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

public class CreateDepartmentService
                implements CreateDepartmentUseCase {

        private final DepartmentRepository departmentRepository;
        private final EventPublisher eventPublisher;

        public CreateDepartmentService(
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher) {
                this.departmentRepository = departmentRepository;
                this.eventPublisher = eventPublisher;
        }

        @Override
        public UUID execute(
                        String departmentName,
                        String abbreviation,
                        DepartmentType departmentType,
                        String location) {

                /*
                 *
                 */
                if (departmentRepository.existsByAbbreviation(abbreviation)) {
                        throw new IllegalArgumentException(
                                        "Department abbreviation already exists: "
                                                        + abbreviation);
                }

                /*
                 *
                 */
                Department department = Department.create(
                                UUID.randomUUID(),
                                departmentName,
                                abbreviation,
                                departmentType,
                                location);

                /*
                 */
                departmentRepository.save(department);

                /*
                 */
                eventPublisher.publish(
                                new DepartmentCreatedEvent(
                                                department.getDepartmentId(),
                                                department.getDepartmentName(),
                                                department.getDepartmentType().name()));

                return department.getDepartmentId();
        }

                public record DepartmentCreatedEvent(
                        UUID departmentId,
                        String departmentName,
                        String departmentType) {
        }
}
