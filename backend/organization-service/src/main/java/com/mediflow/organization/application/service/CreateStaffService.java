package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

/**
 * Application Service thực thi CreateStaffUseCase.
 */
public class CreateStaffService
        implements CreateStaffUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;

    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher
    ) {
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
            String email
    ) {

        /*
         * Staff phải thuộc một Department.
         *
         * Đây là dữ liệu nằm ngoài Staff aggregate,
         * nên Application phải query Repository.
         */
        Department department = departmentRepository
                .findById(departmentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Department not found: "
                                        + departmentId
                        )
                );

        /*
         * Không cho tạo Staff vào Department inactive.
         */
        if (!department.isActive()) {
            throw new IllegalArgumentException(
                    "Department is inactive: "
                            + departmentId
            );
        }

        /*
         * Domain chịu trách nhiệm invariant của Staff.
         *
         * Ví dụ:
         * DOCTOR → licenseNumber bắt buộc.
         */
        Staff staff = Staff.create(
                UUID.randomUUID(),
                fullName,
                departmentId,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email
        );

        /*
         * Persist Staff.
         */
        staffRepository.save(staff);

        /*
         * Publish staff.created.
         */
        eventPublisher.publish(
                new StaffCreatedEvent(
                        staff.getStaffId(),
                        staff.getDepartmentId()
                )
        );

        return staff.getStaffId();
    }

    /**
     * Event staff.created.
     */
    public record StaffCreatedEvent(
            UUID staffId,
            UUID departmentId
    ) {
    }
}
