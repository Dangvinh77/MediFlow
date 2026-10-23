package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.StaffLookupDTO;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.JobTitle;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
public class GetStaffService implements GetStaffUseCase {

    private final StaffRepository staffRepository;

    public GetStaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public Staff getStaffById(UUID id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new StaffNotFoundException(id));
    }

    @Override
    public StaffLookupDTO lookup(UUID id) {
        return staffRepository.findById(id)
                .map(staff -> staff.isEligibleDoctor()
                        ? StaffLookupDTO.eligible(staff.getDepartmentId())
                        : StaffLookupDTO.ineligible())
                .orElseGet(StaffLookupDTO::missing);
    }

    @Override
    public PageResult<Staff> searchDirectory(UUID departmentId, JobTitle jobTitle, String keyword, boolean activeOnly, boolean includeIdentity, PageQuery pageQuery) {
        String normalized = keyword == null ? null : keyword.trim();
        if (normalized != null && normalized.length() > 100) throw new IllegalArgumentException("Search keyword must not exceed 100 characters");
        return staffRepository.searchDirectory(departmentId, jobTitle, normalized, activeOnly, includeIdentity, pageQuery);
    }

    @Override
    public PageResult<Staff> search(
            UUID departmentId,
            JobTitle jobTitle,
            PageQuery pageQuery) {
        return staffRepository.search(departmentId, jobTitle, pageQuery);
    }
}
