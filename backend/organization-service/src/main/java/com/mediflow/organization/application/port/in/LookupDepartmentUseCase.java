package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.DepartmentLookupDTO;

import java.util.UUID;

/** Service-only department identity lookup port. */
public interface LookupDepartmentUseCase {

    DepartmentLookupDTO lookup(UUID departmentId);
}
