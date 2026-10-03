package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.StaffIdentityLookupDTO;
import com.mediflow.organization.application.port.in.LookupStaffIdentityUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Implements the additive, non-doctor-specific staff identity projection. */
@Transactional(readOnly = true)
public class LookupStaffIdentityService implements LookupStaffIdentityUseCase {

    private final StaffRepository staffRepository;

    public LookupStaffIdentityService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public StaffIdentityLookupDTO lookup(UUID staffId) {
        return staffRepository.findById(staffId)
                .map(this::toDto)
                .orElseGet(StaffIdentityLookupDTO::missing);
    }

    private StaffIdentityLookupDTO toDto(Staff staff) {
        return new StaffIdentityLookupDTO(
                true,
                staff.isActive(),
                staff.getJobTitle().name(),
                staff.getDepartmentId());
    }
}
