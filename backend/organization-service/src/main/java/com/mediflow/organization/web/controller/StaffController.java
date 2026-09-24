package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.web.dto.request.ChangeStaffDepartmentRequest;
import com.mediflow.organization.web.dto.request.CreateStaffRequest;
import com.mediflow.organization.web.dto.response.StaffResponse;
import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.StaffLookupDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.web.dto.request.UpdateStaffRequest;
import com.mediflow.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/staff")
public class StaffController {

        private final CreateStaffUseCase createStaffUseCase;
        private final ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase;
        private final GetStaffUseCase getStaffUseCase;
        private final UpdateStaffUseCase updateStaffUseCase;
        private final CorrelationIdProvider correlationIds;

        public StaffController(
                        CreateStaffUseCase createStaffUseCase,
                        ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase,
                        GetStaffUseCase getStaffUseCase,
                        UpdateStaffUseCase updateStaffUseCase,
                        CorrelationIdProvider correlationIds) {
                this.createStaffUseCase = createStaffUseCase;
                this.changeStaffDepartmentUseCase = changeStaffDepartmentUseCase;
                this.getStaffUseCase = getStaffUseCase;
                this.updateStaffUseCase = updateStaffUseCase;
                this.correlationIds = correlationIds;
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
                        @Valid @RequestBody CreateStaffRequest request) {

                Staff staff = createStaffUseCase.execute(
                                request.getFullName(),
                                request.getDepartmentId(),
                                request.getJobTitle(),
                                request.getSpecialization(),
                                request.getLicenseNumber(),
                                request.getPhoneNumber(),
                                request.getEmail());

                return ResponseEntity
                                .created(URI.create("/api/v1/org/staff/" + staff.getStaffId()))
                                .body(ApiResponse.ok(
                                                StaffResponse.from(staff),
                                                correlationIds.currentOrCreate().toString()));
        }

        @PutMapping("/{staffId}/department")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<StaffResponse>> changeDepartment(
                        @PathVariable UUID staffId,
                        @Valid @RequestBody ChangeStaffDepartmentRequest request) {

                Staff staff = changeStaffDepartmentUseCase.execute(
                                staffId,
                                request.getNewDepartmentId());

                return ResponseEntity.ok(ApiResponse.ok(
                                StaffResponse.from(staff),
                                correlationIds.currentOrCreate().toString()));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
        public ResponseEntity<ApiResponse<PageResult<StaffResponse>>> getAllStaff(
                        @RequestParam(name = "departmentId", required = false) UUID departmentId,
                        @RequestParam(name = "jobTitle", required = false) JobTitle jobTitle,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", defaultValue = "20") int size) {
                PageResult<StaffResponse> response = getStaffUseCase
                                .search(departmentId, jobTitle, PageQuery.of(page, size))
                                .map(StaffResponse::from);

                return ResponseEntity.ok(ApiResponse.ok(
                                response,
                                correlationIds.currentOrCreate().toString()));
        }

        @PutMapping("/{staffId}")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
                        @PathVariable UUID staffId,
                        @Valid @RequestBody UpdateStaffRequest request) {
                Staff staff = updateStaffUseCase.execute(
                                staffId,
                                request.getFullName(),
                                request.getJobTitle(),
                                request.getSpecialization(),
                                request.getLicenseNumber(),
                                request.getPhoneNumber(),
                                request.getEmail());

                return ResponseEntity.ok(ApiResponse.ok(
                                StaffResponse.from(staff),
                                correlationIds.currentOrCreate().toString()));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
        public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(@PathVariable UUID id) {
                Staff staff = getStaffUseCase.getStaffById(id);
                return ResponseEntity.ok(ApiResponse.ok(
                                StaffResponse.from(staff),
                                correlationIds.currentOrCreate().toString()));
        }

        @GetMapping("/{id}/exists")
        @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
        public ResponseEntity<ApiResponse<StaffLookupDTO>> staffExists(@PathVariable UUID id) {
                StaffLookupDTO result = getStaffUseCase.lookup(id);
                return ResponseEntity.ok(ApiResponse.ok(
                        result,
                        correlationIds.currentOrCreate().toString()));
        }

}
