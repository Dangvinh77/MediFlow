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
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.web.dto.request.CreateAccountRequest;
import com.mediflow.organization.web.dto.request.UpdateAccountStatusRequest;
import com.mediflow.organization.web.dto.request.VerifyCredentialsRequest;
import com.mediflow.organization.web.dto.response.AccountResponse;
import com.mediflow.organization.web.dto.response.VerifiedAccountResponse;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/accounts")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final UpdateAccountStatusUseCase updateAccountStatusUseCase;
    private final VerifyCredentialsUseCase verifyCredentialsUseCase;

    public AccountController(
            CreateAccountUseCase createAccountUseCase,
            UpdateAccountStatusUseCase updateAccountStatusUseCase,
            VerifyCredentialsUseCase verifyCredentialsUseCase) {
        this.createAccountUseCase = createAccountUseCase;
        this.updateAccountStatusUseCase = updateAccountStatusUseCase;
        this.verifyCredentialsUseCase = verifyCredentialsUseCase;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        Account account = createAccountUseCase.execute(
                request.username(),
                request.password(),
                request.staffId(),
                request.role());

        return ResponseEntity
                .created(URI.create("/api/v1/org/accounts/" + account.getAccountId()))
                .body(AccountResponse.from(account));
    }

    @PutMapping("/{accountId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {

        Account account = updateAccountStatusUseCase.execute(
                accountId,
                request.isActive());

        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<VerifiedAccountResponse> verifyCredentials(
            @Valid @RequestBody VerifyCredentialsRequest request) {

        VerifyCredentialsUseCase.VerifiedAccount account =
                verifyCredentialsUseCase.execute(
                        request.username(),
                        request.password());

        return ResponseEntity.ok(VerifiedAccountResponse.from(account));
    }
}
