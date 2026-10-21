package com.mediflow.organization.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.organization.domain.model.Department;

public interface DepartmentRepository {

        boolean existsByAbbreviation(String abbreviation);

        Optional<Department> findById(UUID departmentId);

    /** Serializes department membership/head/state decisions until transaction commit. */
    Optional<Department> findByIdForUpdate(UUID departmentId);

        Department save(Department department);
    List<Department> findAll();
}
