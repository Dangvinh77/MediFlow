package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.Staff;

/**
 * Application Service thực thi
 * ChangeStaffDepartmentUseCase.
 */
public class ChangeStaffDepartmentService
        implements ChangeStaffDepartmentUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;

    public ChangeStaffDepartmentService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher
    ) {
        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(
            UUID staffId,
            UUID newDepartmentId
    ) {

        /*
         * 1. Tìm Staff hiện tại.
         */
        Staff staff = staffRepository
                .findById(staffId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Staff not found: " + staffId
                        )
                );

        /*
         * 2. Tìm Department mới.
         */
        Department newDepartment = departmentRepository
                .findById(newDepartmentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Department not found: "
                                        + newDepartmentId
                        )
                );

        /*
         * 3. Department mới phải ACTIVE.
         */
        if (!newDepartment.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot transfer staff to inactive department: "
                            + newDepartmentId
            );
        }

        /*
         * 4. Không transfer nếu đã ở Department đó.
         */
        if (staff.getDepartmentId().equals(newDepartmentId)) {
            throw new IllegalArgumentException(
                    "Staff already belongs to department: "
                            + newDepartmentId
            );
        }

        /*
         * Lưu Department cũ để đưa vào event.
         */
        UUID oldDepartmentId = staff.getDepartmentId();

        /*
         * 5. Domain thay đổi state.
         *
         * Không dùng:
         *
         * staff.setDepartmentId(...)
         *
         * mà dùng business behavior của Domain.
         */
        staff.changeDepartment(newDepartmentId);

        /*
         * 6. Persist Staff.
         */
        staffRepository.save(staff);

        /*
         * 7. Publish event.
         *
         * staff.department.changed
         */
        eventPublisher.publish(
                new StaffDepartmentChangedEvent(
                        staff.getStaffId(),
                        oldDepartmentId,
                        newDepartmentId
                )
        );
    }

    /**
     * Event khi Staff chuyển Department.
     */
    public record StaffDepartmentChangedEvent(
            UUID staffId,
            UUID oldDepartmentId,
            UUID newDepartmentId
    ) {
    }
}
