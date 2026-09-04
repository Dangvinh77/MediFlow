package com.mediflow.organization.application.port.in;

import java.util.UUID;

/**
 * Input Port cho use case chuyển Staff sang Department khác.
 */
public interface ChangeStaffDepartmentUseCase {

    /**
     * Chuyển Staff sang Department mới.
     *
     * Không delete/recreate Staff.
     * Chỉ thay đổi department_id.
     */
    void execute(
            UUID staffId,
            UUID newDepartmentId
    );
}
