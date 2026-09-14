package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.GetDepartmentUseCase;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.web.dto.request.CreateDepartmentRequest;
import com.mediflow.organization.web.dto.response.CreateDepartmentResponse;
import com.mediflow.organization.web.dto.response.DepartmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final CreateDepartmentUseCase createDepartmentUseCase;
    private final GetDepartmentUseCase getDepartmentUseCase;

    public DepartmentController(
            CreateDepartmentUseCase createDepartmentUseCase,
            GetDepartmentUseCase getDepartmentUseCase
    ) {
        this.createDepartmentUseCase = createDepartmentUseCase;
        this.getDepartmentUseCase = getDepartmentUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateDepartmentResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request
    ) {
        UUID departmentId = createDepartmentUseCase.execute(
                request.getDepartmentName(),
                request.getAbbreviation(),
                request.getDepartmentType(),
                request.getLocation()
        );

        CreateDepartmentResponse response = new CreateDepartmentResponse(departmentId);

        return ResponseEntity
                .created(URI.create("/departments/" + departmentId))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        List<DepartmentResponse> response = getDepartmentUseCase.getAllDepartments().stream()
                .map(DepartmentResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable UUID id) {
        Department department = getDepartmentUseCase.getDepartmentById(id);
        return ResponseEntity.ok(DepartmentResponse.from(department));
    }
}