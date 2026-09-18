package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.StaffLookupDTO;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import java.util.UUID;

public interface GetStaffUseCase {
    Staff getStaffById(UUID id);

    StaffLookupDTO lookup(UUID id);

    PageResult<Staff> search(UUID departmentId, JobTitle jobTitle, PageQuery pageQuery);
}
