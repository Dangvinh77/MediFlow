package com.mediflow.organization.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.application.dto.response.MailDeliveryDTO;

public record AccountResponse(
        UUID accountId,
        String username,
        UUID staffId,
        UUID patientId,
        Role role,
        boolean isActive,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        MailDeliveryDTO mailDelivery) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getUsername(),
                account.getStaffId(),
                account.getPatientId(),
                account.getRole(),
                account.isActive(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt(), null);
    }

    public static AccountResponse from(Account account, MailDeliveryDTO mailDelivery) {
        return new AccountResponse(account.getAccountId(), account.getUsername(), account.getStaffId(),
                account.getPatientId(), account.getRole(), account.isActive(), account.getLastLoginAt(),
                account.getCreatedAt(), account.getUpdatedAt(), mailDelivery);
    }
}
