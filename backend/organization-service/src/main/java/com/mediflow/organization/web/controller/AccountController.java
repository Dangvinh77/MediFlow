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

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
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
    private final CorrelationIdProvider correlationIds;

    public AccountController(
            CreateAccountUseCase createAccountUseCase,
            UpdateAccountStatusUseCase updateAccountStatusUseCase,
            VerifyCredentialsUseCase verifyCredentialsUseCase,
            CorrelationIdProvider correlationIds) {
        this.createAccountUseCase = createAccountUseCase;
        this.updateAccountStatusUseCase = updateAccountStatusUseCase;
        this.verifyCredentialsUseCase = verifyCredentialsUseCase;
        this.correlationIds = correlationIds;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

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
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
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
