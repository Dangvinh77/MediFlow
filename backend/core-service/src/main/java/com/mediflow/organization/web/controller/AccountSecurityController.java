package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.DemoMailboxAccountDTO;
import com.mediflow.organization.application.dto.response.DemoMailboxConfigDTO;
import com.mediflow.organization.application.dto.response.DemoMailboxMessageDTO;
import com.mediflow.organization.application.dto.response.DemoMailboxMessageDetailDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.service.DemoMailboxQueryService;
import com.mediflow.organization.application.service.SecurityAccountOperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/account-security")
public class AccountSecurityController {
    private final SecurityAccountOperationsService operations;
    private final DemoMailboxQueryService mailbox;
    private final CorrelationIdProvider correlation;

    public AccountSecurityController(SecurityAccountOperationsService operations,
            DemoMailboxQueryService mailbox, CorrelationIdProvider correlation) {
        this.operations = operations; this.mailbox = mailbox; this.correlation = correlation;
    }

    public record ForgotPasswordRequest(@Email @Size(max = 254) String email) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 256) String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) { }

    @PostMapping("/forgot-password")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<SecurityAccountOperationsService.ForgotPasswordResult>> forgot(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return noStore(operations.forgotPassword(request.email()));
    }

    @PostMapping("/reset-password")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<SecurityAccountOperationsService.RecoveryResult>> reset(
            @Valid @RequestBody ResetPasswordRequest request) {
        return noStore(operations.resetPassword(request.token(), request.newPassword()));
    }

    @GetMapping("/demo-mailbox/config")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<DemoMailboxConfigDTO>> mailboxConfig() { return noStore(mailbox.config()); }

    @GetMapping("/demo-mailbox/accounts")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<PageResult<DemoMailboxAccountDTO>>> mailboxAccounts(
            @RequestParam(required = false) String role, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return noStore(mailbox.accounts(role, keyword, PageQuery.of(page, size)));
    }

    @GetMapping("/demo-mailbox/accounts/{accountId}/messages")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<PageResult<DemoMailboxMessageDTO>>> mailboxMessages(
            @PathVariable UUID accountId, @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return noStore(mailbox.messages(accountId, PageQuery.of(page, size)));
    }

    @GetMapping("/demo-mailbox/accounts/{accountId}/messages/{messageId}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and authentication.name == 'gateway'")
    public ResponseEntity<ApiResponse<DemoMailboxMessageDetailDTO>> mailboxMessage(
            @PathVariable UUID accountId, @PathVariable UUID messageId) {
        return noStore(mailbox.message(accountId, messageId));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(data, correlation.currentOrCreate().toString()));
    }
}
