package com.mediflow.organization.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

public record AccountResponse(
        UUID accountId,
        String username,
        UUID staffId,
        Role role,
        boolean isActive,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getUsername(),
                account.getStaffId(),
                account.getRole(),
                account.isActive(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
