package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Department;
import java.util.List;
import java.util.UUID;

public interface GetDepartmentUseCase {
    Department getDepartmentById(UUID id);
    List<Department> getAllDepartments();
}