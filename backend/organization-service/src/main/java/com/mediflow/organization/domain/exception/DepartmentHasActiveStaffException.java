package com.mediflow.organization.domain.exception;

import java.util.UUID;

/**
 * Được throw khi cố gắng deactivate Department
 * nhưng Department vẫn còn Staff đang ACTIVE.
 */
public class DepartmentHasActiveStaffException
        extends DomainException {

    public DepartmentHasActiveStaffException(UUID departmentId) {
        super(
                "Cannot deactivate department because it has active staff: "
                        + departmentId
        );
    }
}
