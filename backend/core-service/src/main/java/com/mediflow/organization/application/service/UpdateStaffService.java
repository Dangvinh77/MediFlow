package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional("organizationTransactionManager")
public class UpdateStaffService implements UpdateStaffUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;

    public UpdateStaffService(StaffRepository staffRepository, DepartmentRepository departmentRepository) {
        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
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
        Staff staff = staffRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new StaffNotFoundException(staffId));

        var department = departmentRepository.findByIdForUpdate(staff.getDepartmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(staff.getDepartmentId()));
        if (staffId.equals(department.getDepartmentHeadId()) && jobTitle != JobTitle.DOCTOR) {
            throw new InvalidDepartmentHeadException(
                    "Remove or reassign the department head before changing their professional title");
        }

        staff.update(
                fullName,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email
        );

        return staffRepository.save(staff);
    }
}
