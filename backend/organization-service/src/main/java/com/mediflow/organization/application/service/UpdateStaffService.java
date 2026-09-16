package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

import java.util.UUID;

public class UpdateStaffService implements UpdateStaffUseCase {

    private final StaffRepository staffRepository;
    private final EventPublisher eventPublisher;

    public UpdateStaffService(
            StaffRepository staffRepository,
            EventPublisher eventPublisher
    ) {
        this.staffRepository = staffRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Staff execute(
            UUID staffId,
            String fullName,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    ) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new StaffNotFoundException(staffId));

        staff.update(
                fullName,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email
        );

        Staff updatedStaff = staffRepository.save(staff);

        eventPublisher.publish(
                new StaffUpdatedEvent(
                        updatedStaff.getStaffId(),
                        updatedStaff.getFullName(),
                        updatedStaff.getDepartmentId(),
                        updatedStaff.getJobTitle().name()
                )
        );

        return updatedStaff;
    }

    public record StaffUpdatedEvent(
            UUID staffId,
            String fullName,
            UUID departmentId,
            String jobTitle
    ) {
    }
}