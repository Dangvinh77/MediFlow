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
import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.service.SecurityAccountOperationsService;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/org/patient-accounts")
public class PatientAccountController {
    private final ProvisionPatientAccountService accounts;
    private final SecurityAccountOperationsService securityOperations;
    private final CorrelationIdProvider correlation;
    public PatientAccountController(ProvisionPatientAccountService accounts,
            SecurityAccountOperationsService securityOperations, CorrelationIdProvider correlation) {
        this.accounts = accounts;
        this.securityOperations = securityOperations;
        this.correlation = correlation;
    }

    public record Request(@NotNull UUID patientId) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("Unsupported account issuance field");
        }
    }
    public record IssuedAccount(UUID accountId, UUID patientId, String username, boolean mustChangePassword,
            MailDeliveryDTO mailDelivery) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<IssuedAccount>> provision(@Valid @RequestBody Request request,
            @RequestHeader("Idempotency-Key") String key, Authentication authentication) {
        var provisioned = accounts.provisionWithMail(UUID.fromString(authentication.getName()), key, request.patientId());
        var result = provisioned.creation();
        var account = result.account();
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .cacheControl(CacheControl.noStore())
                .location(URI.create("/api/v1/org/accounts/" + account.getAccountId()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(new IssuedAccount(account.getAccountId(), account.getPatientId(),
                        account.getUsername(), account.isMustChangePassword(), provisioned.mailDelivery()), correlation.currentOrCreate().toString()));
    }

    @PutMapping("/{patientId}/password")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<MailDeliveryDTO>> resetPassword(@PathVariable UUID patientId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode request, Authentication authentication) {
        if (request == null || !request.isObject() || request.size() != 0) {
            throw new IllegalArgumentException("Patient reset does not accept a caller-supplied password");
        }
        var delivery = securityOperations.resetPatientPassword(UUID.fromString(authentication.getName()),
                patientId, idempotencyKey, correlation.currentOrCreate());
        return noStore(delivery);
    }

    @GetMapping("/{patientId}/mail-delivery")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<MailDeliveryDTO>> latestDelivery(@PathVariable UUID patientId) {
        return noStore(securityOperations.latestPatientDelivery(patientId));
    }

    @PostMapping("/{patientId}/mail-delivery/{messageId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<MailDeliveryDTO>> retryMail(@PathVariable UUID patientId,
            @PathVariable UUID messageId, Authentication authentication) {
        var delivery = securityOperations.retryPatientDelivery(UUID.fromString(authentication.getName()),
                patientId, messageId, correlation.currentOrCreate());
        return noStore(delivery);
    }

    private ResponseEntity<ApiResponse<MailDeliveryDTO>> noStore(MailDeliveryDTO delivery) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.ok(delivery, correlation.currentOrCreate().toString()));
    }
}
