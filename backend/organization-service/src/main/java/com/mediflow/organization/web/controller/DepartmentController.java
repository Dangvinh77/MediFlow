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
import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.web.dto.request.UpdateDepartmentRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/departments")
public class DepartmentController {

        private final CreateDepartmentUseCase createDepartmentUseCase;
        private final GetDepartmentUseCase getDepartmentUseCase;
        private final UpdateDepartmentUseCase updateDepartmentUseCase;

        public DepartmentController(
                        CreateDepartmentUseCase createDepartmentUseCase,
                        GetDepartmentUseCase getDepartmentUseCase,
                        UpdateDepartmentUseCase updateDepartmentUseCase) {
                this.createDepartmentUseCase = createDepartmentUseCase;
                this.getDepartmentUseCase = getDepartmentUseCase;
                this.updateDepartmentUseCase = updateDepartmentUseCase;
        }

        @PostMapping
        public ResponseEntity<CreateDepartmentResponse> createDepartment(
                        @Valid @RequestBody CreateDepartmentRequest request) {
                UUID departmentId = createDepartmentUseCase.execute(
                                request.getDepartmentName(),
                                request.getAbbreviation(),
                                request.getDepartmentType(),
                                request.getLocation());

                CreateDepartmentResponse response = new CreateDepartmentResponse(departmentId);

                return ResponseEntity
                                .created(URI.create("/api/v1/org/departments/" + departmentId))
                                .body(response);
        }

        @GetMapping
        public ResponseEntity<List<DepartmentResponse>> getAllDepartments(
                        @RequestParam(name = "activeOnly", required = false) Boolean activeOnly) {
                List<DepartmentResponse> response = getDepartmentUseCase.getAllDepartments().stream()
                                .filter(department -> activeOnly == null || !activeOnly || department.isActive())
                                .map(DepartmentResponse::from)
                                .toList();
                return ResponseEntity.ok(response);
        }

        @GetMapping("/{id}")
        public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable UUID id) {
                Department department = getDepartmentUseCase.getDepartmentById(id);
                return ResponseEntity.ok(DepartmentResponse.from(department));
        }

        @PutMapping("/{id}")
        public ResponseEntity<DepartmentResponse> updateDepartment(
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateDepartmentRequest request) {
                Department department = updateDepartmentUseCase.execute(
                                id,
                                request.getDepartmentName(),
                                request.getDepartmentType(),
                                request.getLocation());

                return ResponseEntity.ok(DepartmentResponse.from(department));
        }
}
