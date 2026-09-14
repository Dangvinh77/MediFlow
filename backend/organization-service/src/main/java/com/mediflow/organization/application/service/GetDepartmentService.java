package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.GetDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.model.Department;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Transactional(readOnly = true)
public class GetDepartmentService implements GetDepartmentUseCase {

    private final DepartmentRepository departmentRepository;

    public GetDepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
    }

    @Override
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }
}