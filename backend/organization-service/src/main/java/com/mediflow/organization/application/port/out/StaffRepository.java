package com.mediflow.organization.application.port.out;


import java.util.Optional;
import java.util.UUID;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

public interface StaffRepository {

        boolean existsByDepartmentIdAndActiveTrue(UUID departmentId);

        Optional<Staff> findById(UUID staffId);

        Staff save(Staff staff);

        PageResult<Staff> search(
                UUID departmentId,
                JobTitle jobTitle,
                PageQuery pageQuery);
    
}
