package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.request.*;
import com.mediflow.organization.application.dto.response.*;
import com.mediflow.organization.application.port.in.ManageSurgeryAuthorityUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.model.SurgicalTeamRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org")
public class SurgeryAuthorityController {
    private final ManageSurgeryAuthorityUseCase authority;
    private final CorrelationIdProvider correlations;

    public SurgeryAuthorityController(ManageSurgeryAuthorityUseCase authority, CorrelationIdProvider correlations) {
        this.authority = authority;
        this.correlations = correlations;
    }

    @PostMapping("/operating-rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OperatingRoomDTO>> createRoom(
            @Valid @RequestBody OperatingRoomRequest request, Principal principal) {
        if (request.expectedRevision() != 0) throw new IllegalArgumentException("Creation requires revision zero");
        UUID id = UUID.randomUUID();
        OperatingRoomDTO room = authority.saveRoom(id, request, accountId(principal));
        return ResponseEntity.created(URI.create("/api/v1/org/operating-rooms/" + id))
                .body(envelope(room));
    }

    @PutMapping("/operating-rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OperatingRoomDTO> updateRoom(@PathVariable UUID id,
            @Valid @RequestBody OperatingRoomRequest request, Principal principal) {
        if (request.expectedRevision() == 0) throw new IllegalArgumentException("Update requires positive revision");
        return envelope(authority.saveRoom(id, request, accountId(principal)));
    }

    @PutMapping("/staff/{id}/surgery-roles/{teamRole}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SurgicalCapabilityDTO> decideCapability(@PathVariable UUID id,
            @PathVariable SurgicalTeamRole teamRole, @Valid @RequestBody SurgicalCapabilityRequest request,
            Principal principal) {
        return envelope(authority.decideCapability(id, teamRole, request, accountId(principal)));
    }

    @GetMapping("/operating-rooms/{id}/lookup")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
    public ApiResponse<OperatingRoomLookupDTO> lookupRoom(@PathVariable UUID id) {
        return envelope(authority.lookupRoom(id));
    }

    @GetMapping("/staff/{id}/surgery-eligibility")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
    public ApiResponse<SurgicalEligibilityDTO> lookupEligibility(@PathVariable UUID id,
            @RequestParam SurgicalTeamRole teamRole, @RequestParam Instant startsAt, @RequestParam Instant endsAt) {
        return envelope(authority.lookupEligibility(id, teamRole, startsAt, endsAt));
    }

    private <T> ApiResponse<T> envelope(T data) {
        return ApiResponse.ok(data, correlations.currentOrCreate().toString());
    }

    private static UUID accountId(Principal principal) {
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException invalid) {
            throw new org.springframework.security.access.AccessDeniedException("Verified account UUID is required");
        }
    }
}
