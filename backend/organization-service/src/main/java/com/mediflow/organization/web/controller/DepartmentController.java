package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.GetDepartmentUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.web.dto.request.CreateDepartmentRequest;
import com.mediflow.organization.web.dto.response.DepartmentResponse;
import com.mediflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
        private final CorrelationIdProvider correlationIds;

        public DepartmentController(
                        CreateDepartmentUseCase createDepartmentUseCase,
                        GetDepartmentUseCase getDepartmentUseCase,
                        UpdateDepartmentUseCase updateDepartmentUseCase,
                        CorrelationIdProvider correlationIds) {
                this.createDepartmentUseCase = createDepartmentUseCase;
                this.getDepartmentUseCase = getDepartmentUseCase;
                this.updateDepartmentUseCase = updateDepartmentUseCase;
                this.correlationIds = correlationIds;
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
                        @Valid @RequestBody CreateDepartmentRequest request) {
                Department department = createDepartmentUseCase.execute(
                                request.getDepartmentName(),
                                request.getAbbreviation(),
                                request.getDepartmentType(),
                                request.getLocation());

                return ResponseEntity
                                .created(URI.create("/api/v1/org/departments/"
                                                + department.getDepartmentId()))
                                .body(ApiResponse.ok(
                                                DepartmentResponse.from(department),
                                                correlationIds.currentOrCreate().toString()));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
        public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllDepartments(
                        @RequestParam(name = "activeOnly", required = false) Boolean activeOnly) {
                List<DepartmentResponse> response = getDepartmentUseCase.getAllDepartments().stream()
                                .filter(department -> activeOnly == null || !activeOnly || department.isActive())
                                .map(DepartmentResponse::from)
                                .toList();
                return ResponseEntity.ok(ApiResponse.ok(
                                response,
                                correlationIds.currentOrCreate().toString()));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
        public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(
                        @PathVariable UUID id) {
                Department department = getDepartmentUseCase.getDepartmentById(id);
                return ResponseEntity.ok(ApiResponse.ok(
                                DepartmentResponse.from(department),
                                correlationIds.currentOrCreate().toString()));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateDepartmentRequest request) {
                Department department = updateDepartmentUseCase.execute(
                                id,
                                request.getDepartmentName(),
                                request.getDepartmentType(),
                                request.getLocation(),
                                request.getDepartmentHeadId(),
                                request.getActive());

                return ResponseEntity.ok(ApiResponse.ok(
                                DepartmentResponse.from(department),
                                correlationIds.currentOrCreate().toString()));
        }
}
