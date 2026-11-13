package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.port.in.DeactivateStaffUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.web.dto.response.StaffResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/staff")
public class StaffLifecycleController {
    private final DeactivateStaffUseCase lifecycle;
    private final CorrelationIdProvider correlationIds;

    public StaffLifecycleController(DeactivateStaffUseCase lifecycle, CorrelationIdProvider correlationIds) {
        this.lifecycle = lifecycle;
        this.correlationIds = correlationIds;
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> deactivate(@PathVariable UUID id, Authentication authentication) {
        var result = lifecycle.execute(id, UUID.fromString(authentication.getName()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(StaffResponse.from(result), correlationIds.currentOrCreate().toString()));
    }
}
