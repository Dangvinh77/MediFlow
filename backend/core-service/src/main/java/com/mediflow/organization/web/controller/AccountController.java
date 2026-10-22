package com.mediflow.organization.web.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.Authentication;

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.application.port.in.LookupAccountIdentityUseCase;
import com.mediflow.organization.application.dto.response.AccountIdentityLookupDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.web.dto.request.CreateAccountRequest;
import com.mediflow.organization.web.dto.request.UpdateAccountStatusRequest;
import com.mediflow.organization.web.dto.request.VerifyCredentialsRequest;
import com.mediflow.organization.web.dto.response.AccountResponse;
import com.mediflow.organization.web.dto.response.VerifiedAccountResponse;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/accounts")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final UpdateAccountStatusUseCase updateAccountStatusUseCase;
    private final VerifyCredentialsUseCase verifyCredentialsUseCase;
    private final LookupAccountIdentityUseCase lookupAccountIdentityUseCase;
    private final CorrelationIdProvider correlationIds;

    public AccountController(
            CreateAccountUseCase createAccountUseCase,
            UpdateAccountStatusUseCase updateAccountStatusUseCase,
            VerifyCredentialsUseCase verifyCredentialsUseCase,
            LookupAccountIdentityUseCase lookupAccountIdentityUseCase,
            CorrelationIdProvider correlationIds) {
        this.createAccountUseCase = createAccountUseCase;
        this.updateAccountStatusUseCase = updateAccountStatusUseCase;
        this.verifyCredentialsUseCase = verifyCredentialsUseCase;
        this.lookupAccountIdentityUseCase = lookupAccountIdentityUseCase;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/{accountId}/identity")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<AccountIdentityLookupDTO>> lookupIdentity(
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok(
                lookupAccountIdentityUseCase.lookup(accountId),
                correlationIds.currentOrCreate().toString()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        if (idempotencyKey != null) {
            if (idempotencyKey.isBlank() || idempotencyKey.length() > 160
                    || idempotencyKey.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
                throw new IllegalArgumentException("Invalid Idempotency-Key");
            }
            UUID actorAccountId;
            try {
                actorAccountId = UUID.fromString(authentication.getName());
            } catch (RuntimeException invalidPrincipal) {
                throw new IllegalArgumentException("Authenticated account identity is invalid");
            }
            var result = createAccountUseCase.executeWithIdempotency(actorAccountId, idempotencyKey,
                    request.username(), request.password(), request.staffId(), request.patientId(), request.role());
            return ResponseEntity.status(result.replayed()
                            ? org.springframework.http.HttpStatus.OK : org.springframework.http.HttpStatus.CREATED)
                    .location(URI.create("/api/v1/org/accounts/" + result.account().getAccountId()))
                    .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                    .body(ApiResponse.ok(AccountResponse.from(result.account()),
                            correlationIds.currentOrCreate().toString()));
        }

        Account account = createAccountUseCase.execute(
                request.username(),
                request.password(),
                request.staffId(),
                request.patientId(),
                request.role());

        return ResponseEntity
                .created(URI.create("/api/v1/org/accounts/" + account.getAccountId()))
                .body(ApiResponse.ok(
                        AccountResponse.from(account),
                        correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/{accountId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> updateStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {

        Account account = updateAccountStatusUseCase.execute(
                accountId,
                request.isActive());

        return ResponseEntity.ok(ApiResponse.ok(
                AccountResponse.from(account),
                correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<VerifiedAccountResponse>> verifyCredentials(
            @Valid @RequestBody VerifyCredentialsRequest request) {

        VerifyCredentialsUseCase.VerifiedAccount account =
                verifyCredentialsUseCase.execute(
                        request.username(),
                        request.password());

        return ResponseEntity.ok(ApiResponse.ok(
                VerifiedAccountResponse.from(account),
                correlationIds.currentOrCreate().toString()));
    }
}
