package com.mediflow.organization.domain.exception;

import java.util.UUID;

/** Thrown when a department still has active staff during deactivation. */
public class DepartmentHasActiveStaffException
        extends DomainException {

    public DepartmentHasActiveStaffException(UUID departmentId) {
        super(
                "Cannot deactivate department because it has active staff: "
                        + departmentId
        );
    }
}
