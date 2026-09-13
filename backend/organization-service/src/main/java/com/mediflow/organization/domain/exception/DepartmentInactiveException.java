package com.mediflow.organization.domain.exception;

import java.util.UUID;

public class DepartmentInactiveException extends RuntimeException {

    public DepartmentInactiveException(UUID departmentId) {
        super("Department is inactive: " + departmentId);
    }
}