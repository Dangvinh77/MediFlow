package com.mediflow.organization.application.service;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.application.event.StaffDepartmentChangedEvent;
import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class ChangeStaffDepartmentService
        implements ChangeStaffDepartmentUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;
    private final CorrelationIdProvider correlationIds;

    public ChangeStaffDepartmentService(
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
    public ChangeStaffDepartmentService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher) {
        this(staffRepository, departmentRepository, eventPublisher, UUID::randomUUID);
    }

    @Override
    public Staff execute(
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
            return staff;
        }

        UUID oldDepartmentId = staff.getDepartmentId();

        staff.changeDepartment(newDepartmentId);

        Staff savedStaff = staffRepository.save(staff);

        eventPublisher.publishStaffDepartmentChanged(
                new StaffDepartmentChangedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        correlationIds.currentOrCreate().toString(),
                        savedStaff.getStaffId(),
                        oldDepartmentId,
                        newDepartmentId
                )
        );

        return savedStaff;
    }
}
