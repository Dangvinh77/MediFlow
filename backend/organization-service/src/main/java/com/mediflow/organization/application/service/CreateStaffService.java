package com.mediflow.organization.application.service;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.application.event.StaffCreatedEvent;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CreateStaffService implements CreateStaffUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;
    private final CorrelationIdProvider correlationIds;

    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher,
            CorrelationIdProvider correlationIds) {

        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
        this.correlationIds = correlationIds;
    }

    /** Compatibility constructor for direct application-layer tests. */
    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher) {
        this(staffRepository, departmentRepository, eventPublisher, UUID::randomUUID);
    }

    @Override
    public Staff execute(
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

        Staff savedStaff = staffRepository.save(staff);

        eventPublisher.publishStaffCreated(
                new StaffCreatedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        correlationIds.currentOrCreate().toString(),
                        savedStaff.getStaffId(),
                        savedStaff.getFullName(),
                        savedStaff.getDepartmentId(),
                        savedStaff.getJobTitle().name()
                )
        );

        return savedStaff;
    }

}
