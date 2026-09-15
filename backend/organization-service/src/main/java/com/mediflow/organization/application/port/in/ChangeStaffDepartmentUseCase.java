package com.mediflow.organization.application.port.in;

import java.util.UUID;

public interface ChangeStaffDepartmentUseCase {

        void execute(
            UUID staffId,
            UUID newDepartmentId
    );
}
