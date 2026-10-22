package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

import com.mediflow.organization.domain.model.DepartmentProfile;

public interface CreateDepartmentUseCase {

        Department execute(
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location
    );
    Department execute(String departmentName, String abbreviation, DepartmentType departmentType, String location, DepartmentProfile profile);
}
