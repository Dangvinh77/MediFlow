package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

import java.util.UUID;

public interface UpdateDepartmentUseCase {

    Department execute(
            UUID departmentId,
            String departmentName,
            DepartmentType departmentType,
            String location
    );
}