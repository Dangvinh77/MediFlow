package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.port.in.LookupProfileAccountUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.web.dto.response.ProfileAccountResponse;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/org/profile-accounts")
public class ProfileAccountController {
    private final LookupProfileAccountUseCase profiles;
    private final CorrelationIdProvider correlationIds;

    public ProfileAccountController(LookupProfileAccountUseCase profiles,
            CorrelationIdProvider correlationIds) {
        this.profiles = profiles;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/staff/{staffId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileAccountResponse>> staff(@PathVariable UUID staffId) {
        return response(ProfileAccountResponse.from(profiles.staff(staffId)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<ProfileAccountResponse>> patient(@PathVariable UUID patientId) {
        return response(ProfileAccountResponse.from(profiles.patient(patientId)));
    }

    private ResponseEntity<ApiResponse<ProfileAccountResponse>> response(ProfileAccountResponse data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(data, correlationIds.currentOrCreate().toString()));
    }
}
