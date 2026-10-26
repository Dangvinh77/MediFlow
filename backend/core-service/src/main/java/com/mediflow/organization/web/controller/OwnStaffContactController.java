package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.port.in.UpdateOwnStaffContactUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.infrastructure.security.StaffIdentityDetails;
import com.mediflow.organization.web.dto.request.UpdateOwnStaffContactRequest;
import com.mediflow.organization.web.dto.response.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/staff")
public class OwnStaffContactController {
    private final UpdateOwnStaffContactUseCase contacts;
    private final CorrelationIdProvider correlationIds;

    public OwnStaffContactController(UpdateOwnStaffContactUseCase contacts, CorrelationIdProvider correlationIds) {
        this.contacts = contacts;
        this.correlationIds = correlationIds;
    }

    @PutMapping("/{id}/contact")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH') and "
            + "authentication.details instanceof T(com.mediflow.organization.infrastructure.security.StaffIdentityDetails) "
            + "and authentication.details.staffId == #id")
    public ResponseEntity<ApiResponse<StaffResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateOwnStaffContactRequest request, Authentication authentication) {
        UUID signedStaffId = ((StaffIdentityDetails) authentication.getDetails()).getStaffId();
        var updated = contacts.update(id, signedStaffId, request.phoneNumber(), request.email(), request.address());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                StaffResponse.from(updated), correlationIds.currentOrCreate().toString()));
    }
}
