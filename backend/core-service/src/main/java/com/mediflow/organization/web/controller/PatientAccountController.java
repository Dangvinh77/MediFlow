package com.mediflow.organization.web.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.service.ProvisionPatientAccountService;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/patient-accounts")
public class PatientAccountController {
    private final ProvisionPatientAccountService accounts;
    private final CorrelationIdProvider correlation;
    public PatientAccountController(ProvisionPatientAccountService accounts, CorrelationIdProvider correlation) {
        this.accounts = accounts; this.correlation = correlation;
    }

    public record Request(@NotNull UUID patientId) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("Unsupported account issuance field");
        }
    }
    public record IssuedAccount(UUID accountId, UUID patientId, String username, boolean mustChangePassword) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<IssuedAccount>> provision(@Valid @RequestBody Request request,
            @RequestHeader("Idempotency-Key") String key, Authentication authentication) {
        var result = accounts.provision(UUID.fromString(authentication.getName()), key, request.patientId());
        var account = result.account();
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .cacheControl(CacheControl.noStore())
                .location(URI.create("/api/v1/org/accounts/" + account.getAccountId()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(new IssuedAccount(account.getAccountId(), account.getPatientId(),
                        account.getUsername(), account.isMustChangePassword()), correlation.currentOrCreate().toString()));
    }
}
