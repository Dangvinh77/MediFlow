package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

public interface CreateDepartmentUseCase {

        Department execute(
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location
    );
}
