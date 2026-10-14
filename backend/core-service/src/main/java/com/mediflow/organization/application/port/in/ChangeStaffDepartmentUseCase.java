package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.Staff;

public interface ChangeStaffDepartmentUseCase {

        Staff execute(
            UUID staffId,
            UUID newDepartmentId
    );
}
