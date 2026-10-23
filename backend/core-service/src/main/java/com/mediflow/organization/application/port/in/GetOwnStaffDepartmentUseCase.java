package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.StaffDepartmentDTO;
import java.util.UUID;

public interface GetOwnStaffDepartmentUseCase {
    StaffDepartmentDTO read(UUID requestedStaffId, UUID signedStaffId);
}
