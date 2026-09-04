package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.DepartmentType;

/**
 * Input Port cho use case tạo Department.
 *
 * Controller sẽ gọi interface này,
 * không gọi trực tiếp CreateDepartmentService.
 */
public interface CreateDepartmentUseCase {

    /**
     * Tạo một Department mới.
     *
     * @return ID của Department vừa tạo
     */
    UUID execute(
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location
    );
}
