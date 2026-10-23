package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.response.StaffDepartmentDTO;
import com.mediflow.organization.application.port.in.GetOwnStaffDepartmentUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.infrastructure.security.StaffIdentityDetails;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/staff")
public class OwnStaffDepartmentController {
    private final GetOwnStaffDepartmentUseCase profiles;
    private final CorrelationIdProvider correlationIds;

    public OwnStaffDepartmentController(GetOwnStaffDepartmentUseCase profiles, CorrelationIdProvider correlationIds) {
        this.profiles = profiles;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/{id}/department")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH') and "
            + "authentication.details instanceof T(com.mediflow.organization.infrastructure.security.StaffIdentityDetails) "
            + "and authentication.details.staffId == #id")
    public ResponseEntity<ApiResponse<StaffDepartmentDTO>> read(@PathVariable UUID id, Authentication authentication) {
        UUID signedStaffId = ((StaffIdentityDetails) authentication.getDetails()).getStaffId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(profiles.read(id, signedStaffId), correlationIds.currentOrCreate().toString()));
    }
}
