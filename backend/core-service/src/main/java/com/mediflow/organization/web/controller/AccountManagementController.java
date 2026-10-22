package com.mediflow.organization.web.controller;

import java.util.UUID;
import jakarta.validation.Valid;
import com.mediflow.organization.web.dto.request.ChangePasswordRequest;
import com.mediflow.organization.web.dto.request.ResetPasswordRequest;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.port.in.ManageAccountsUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.web.dto.response.AccountResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/org/accounts")
public class AccountManagementController {
    private final ManageAccountsUseCase accounts;
    private final CorrelationIdProvider correlationIds;

    public AccountManagementController(ManageAccountsUseCase accounts, CorrelationIdProvider correlationIds) {
        this.accounts = accounts;
        this.correlationIds = correlationIds;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResult<AccountResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = accounts.search(keyword, role, active, PageQuery.of(page, size)).map(AccountResponse::from);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(result, correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> getById(@PathVariable UUID accountId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                AccountResponse.from(accounts.getById(accountId)), correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/me/password")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH','PATIENT')")
    public ResponseEntity<ApiResponse<Boolean>> changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword(UUID.fromString(authentication.getName()), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(true, correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/{accountId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> resetPassword(@PathVariable UUID accountId,
            @Valid @RequestBody ResetPasswordRequest request) {
        accounts.resetPassword(accountId, request.newPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(true, correlationIds.currentOrCreate().toString()));
    }

}
