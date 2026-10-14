package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class UpdateStaffService implements UpdateStaffUseCase {

    private final StaffRepository staffRepository;
    public UpdateStaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
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

        return updatedStaff;
    }
}
