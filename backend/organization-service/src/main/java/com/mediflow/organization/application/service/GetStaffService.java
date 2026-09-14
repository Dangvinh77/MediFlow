package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
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
    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    @Override
    public List<Staff> getStaffByDepartmentId(UUID departmentId) {
        return staffRepository.findByDepartmentId(departmentId);
    }
}