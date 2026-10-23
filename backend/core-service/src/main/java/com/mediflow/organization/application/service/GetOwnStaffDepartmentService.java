package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.StaffDepartmentDTO;
import com.mediflow.organization.application.port.in.GetOwnStaffDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Transactional(readOnly = true)
public class GetOwnStaffDepartmentService implements GetOwnStaffDepartmentUseCase {
    private final StaffRepository staff;
    private final DepartmentRepository departments;

    public GetOwnStaffDepartmentService(StaffRepository staff, DepartmentRepository departments) {
        this.staff = staff;
        this.departments = departments;
    }

    @Override
    public StaffDepartmentDTO read(UUID requestedStaffId, UUID signedStaffId) {
        if (signedStaffId == null || !signedStaffId.equals(requestedStaffId)) {
            throw new IllegalArgumentException("Own staff identity is required");
        }
        var member = staff.findById(requestedStaffId).orElseThrow(() -> new StaffNotFoundException(requestedStaffId));
        var department = departments.findById(member.getDepartmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(member.getDepartmentId()));
        return new StaffDepartmentDTO(department.getDepartmentId(), department.getDepartmentName());
    }
}
