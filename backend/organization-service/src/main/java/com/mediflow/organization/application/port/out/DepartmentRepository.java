package com.mediflow.organization.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.organization.domain.model.Department;

/**
 * Output Port cho việc persistence Department.
 *
 * Application chỉ biết interface này.
 * Không biết JPA hay PostgreSQL.
 */
public interface DepartmentRepository {

    /**
     * Kiểm tra abbreviation đã tồn tại chưa.
     */
    boolean existsByAbbreviation(String abbreviation);

    /**
     * Tìm Department theo ID.
     */
    Optional<Department> findById(UUID departmentId);

    /**
     * Lưu Department.
     */
    Department save(Department department);
}
