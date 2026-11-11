package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.request.CapabilityGrantCommand;
import com.mediflow.organization.application.dto.request.CapabilityRevocationCommand;
import com.mediflow.organization.application.dto.response.LabReleaseAuthority;
import com.mediflow.organization.application.dto.response.RecordReviewAuthority;
import com.mediflow.organization.application.dto.response.StaffCapabilityGrantDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.service.StaffCapabilityGrantService;
import com.mediflow.organization.domain.model.Role;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class StaffCapabilityGrantController {
    private final StaffCapabilityGrantService service;
    private final CorrelationIdProvider correlationIds;

    public StaffCapabilityGrantController(StaffCapabilityGrantService service, CorrelationIdProvider correlationIds) {
        this.service = service;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/org/staff/{staffId}/capability-grants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffCapabilityGrantDTO>> grant(
            @PathVariable UUID staffId,
            Authentication authentication,
            @Valid @RequestBody CapabilityGrantCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.grant(accountId(authentication), Role.ADMIN, staffId, command),
                correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/api/v2/org/capability-grants/{grantId}/revocation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffCapabilityGrantDTO>> revoke(
            @PathVariable UUID grantId,
            Authentication authentication,
            @Valid @RequestBody CapabilityRevocationCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.revoke(accountId(authentication), Role.ADMIN, grantId, command),
                correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/org/lab-release-authority")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and principal == 'lab-service'")
    public ResponseEntity<ApiResponse<LabReleaseAuthority>> labReleaseAuthority(@RequestParam UUID actorAccountId) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(service.labReleaseAuthority(actorAccountId),
                        correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/org/record-review-authority")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and principal == 'clinical-service'")
    public ResponseEntity<ApiResponse<RecordReviewAuthority>> recordReviewAuthority(@RequestParam UUID actorAccountId) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(service.recordReviewAuthority(actorAccountId),
                        correlationIds.currentOrCreate().toString()));
    }

    private static UUID accountId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException invalid) {
            throw new AccessDeniedException("A verified account UUID is required");
        }
    }
}
