package com.mediflow.organization.domain.exception;

import java.util.UUID;

public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(UUID departmentId) {
        super("Department not found: " + departmentId);
    }
}