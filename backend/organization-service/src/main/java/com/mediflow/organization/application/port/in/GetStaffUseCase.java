package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Staff;
import java.util.List;
import java.util.UUID;

public interface GetStaffUseCase {
    Staff getStaffById(UUID id);
    List<Staff> getAllStaff();
    List<Staff> getStaffByDepartmentId(UUID departmentId);
}