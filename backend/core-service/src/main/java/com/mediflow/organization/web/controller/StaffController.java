package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.application.port.in.LookupStaffIdentityUseCase;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.web.dto.request.ChangeStaffDepartmentRequest;
import com.mediflow.organization.web.dto.request.CreateStaffRequest;
import com.mediflow.organization.web.dto.response.StaffResponse;
import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.StaffLookupDTO;
import com.mediflow.organization.application.dto.response.StaffIdentityLookupDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.web.dto.request.UpdateStaffRequest;
import com.mediflow.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
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
        private final LookupStaffIdentityUseCase lookupStaffIdentityUseCase;
        private final CorrelationIdProvider correlationIds;

        public StaffController(
                        CreateStaffUseCase createStaffUseCase,
                        ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase,
                        GetStaffUseCase getStaffUseCase,
                        UpdateStaffUseCase updateStaffUseCase,
                        LookupStaffIdentityUseCase lookupStaffIdentityUseCase,
                        CorrelationIdProvider correlationIds) {
                this.createStaffUseCase = createStaffUseCase;
                this.changeStaffDepartmentUseCase = changeStaffDepartmentUseCase;
                this.getStaffUseCase = getStaffUseCase;
                this.updateStaffUseCase = updateStaffUseCase;
                this.lookupStaffIdentityUseCase = lookupStaffIdentityUseCase;
                this.correlationIds = correlationIds;
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
                        @Valid @RequestBody CreateStaffRequest request,
                        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                        Authentication authentication) {

                if (idempotencyKey != null) {
                        validateIdempotencyKey(idempotencyKey);
                        UUID actorAccountId;
                        try {
                                actorAccountId = UUID.fromString(authentication.getName());
                        } catch (RuntimeException invalidPrincipal) {
                                throw new IllegalArgumentException("Authenticated account identity is invalid");
                        }
                        var result = createStaffUseCase.executeWithIdempotency(
                                        actorAccountId,
                                        idempotencyKey,
                                        request.fullName(),
                                        request.departmentId(),
                                        request.jobTitle(),
                                        request.specialization(),
                                        request.licenseNumber(),
                                        request.phoneNumber(),
                                        request.email(), request.identityNumber());
                        URI location = URI.create("/api/v1/org/staff/" + result.staff().getStaffId());
                        return ResponseEntity.status(result.replayed()
                                                ? org.springframework.http.HttpStatus.OK
                                                : org.springframework.http.HttpStatus.CREATED)
                                        .location(location)
                                        .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                                        .body(ApiResponse.ok(
                                                        StaffResponse.from(result.staff()),
                                                        correlationIds.currentOrCreate().toString()));
                }

                Staff staff = createStaffUseCase.execute(
                                request.fullName(),
                                request.departmentId(),
                                request.jobTitle(),
                                request.specialization(),
                                request.licenseNumber(),
                                request.phoneNumber(),
                                request.email(), request.identityNumber());

                return ResponseEntity
                                .created(URI.create("/api/v1/org/staff/" + staff.getStaffId()))
                                .body(ApiResponse.ok(
                                                StaffResponse.from(staff),
                                                correlationIds.currentOrCreate().toString()));
        }

        private void validateIdempotencyKey(String key) {
                if (key.isBlank() || key.length() > 160
                                || key.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
                        throw new IllegalArgumentException("Invalid Idempotency-Key");
                }
        }

        @PutMapping("/{staffId}/department")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<StaffResponse>> changeDepartment(
                        @PathVariable UUID staffId,
                        @Valid @RequestBody ChangeStaffDepartmentRequest request) {

                Staff staff = changeStaffDepartmentUseCase.execute(
                                staffId,
                                request.newDepartmentId());

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
                        @RequestParam(name = "size", defaultValue = "20") int size,
                        Authentication authentication) {
                PageResult<StaffResponse> response = getStaffUseCase
                                .search(departmentId, jobTitle, PageQuery.of(page, size))
                                .map(staff -> readableStaff(staff, authentication));

                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
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
                                request.fullName(),
                                request.jobTitle(),
                                request.specialization(),
                                request.licenseNumber(),
                                request.phoneNumber(),
                                request.email());

                return ResponseEntity.ok(ApiResponse.ok(
                                StaffResponse.from(staff),
                                correlationIds.currentOrCreate().toString()));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
        public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(@PathVariable UUID id, Authentication authentication) {
                Staff staff = getStaffUseCase.getStaffById(id);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                                readableStaff(staff, authentication),
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

        @GetMapping("/{id}/lookup")
        @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
        public ResponseEntity<ApiResponse<StaffIdentityLookupDTO>> lookupStaffIdentity(
                        @PathVariable UUID id) {
                StaffIdentityLookupDTO result = lookupStaffIdentityUseCase.lookup(id);
                return ResponseEntity.ok(ApiResponse.ok(
                                result,
                                correlationIds.currentOrCreate().toString()));
        }

        private StaffResponse readableStaff(Staff staff, Authentication authentication) {
                boolean admin = authentication != null && authentication.getAuthorities().stream()
                                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                return admin ? StaffResponse.from(staff) : StaffResponse.directory(staff);
        }

}
