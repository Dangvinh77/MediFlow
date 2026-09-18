package com.mediflow.organization.application.service;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.event.DepartmentCreatedEvent;
import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CreateDepartmentService
                implements CreateDepartmentUseCase {

        private final DepartmentRepository departmentRepository;
        private final EventPublisher eventPublisher;
        private final CorrelationIdProvider correlationIds;

        public CreateDepartmentService(
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher,
                        CorrelationIdProvider correlationIds) {
                this.departmentRepository = departmentRepository;
                this.eventPublisher = eventPublisher;
                this.correlationIds = correlationIds;
        }

        /** Compatibility constructor for direct application-layer tests. */
        public CreateDepartmentService(
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher) {
                this(departmentRepository, eventPublisher, UUID::randomUUID);
        }

        @Override
        public Department execute(
                        String departmentName,
                        String abbreviation,
                        DepartmentType departmentType,
                        String location) {

                /*
                 *
                 */
                if (departmentRepository.existsByAbbreviation(abbreviation)) {
                        throw new DuplicateResourceException(
                                        "DEPARTMENT_ABBREVIATION_DUPLICATE",
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
                Department savedDepartment = departmentRepository.save(department);

                /*
                 */
                eventPublisher.publishDepartmentCreated(
                                new DepartmentCreatedEvent(
                                                UUID.randomUUID(),
                                                Instant.now(),
                                                correlationIds.currentOrCreate().toString(),
                                                savedDepartment.getDepartmentId(),
                                                savedDepartment.getDepartmentName(),
                                                savedDepartment.getDepartmentType().name()));

                return savedDepartment;
        }
}
