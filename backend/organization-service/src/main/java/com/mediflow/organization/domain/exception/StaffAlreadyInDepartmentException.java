package com.mediflow.organization.domain.exception;

import java.util.UUID;

public class StaffAlreadyInDepartmentException extends RuntimeException {

    public StaffAlreadyInDepartmentException(UUID departmentId) {
        super("Staff already belongs to department: " + departmentId);
    }
}