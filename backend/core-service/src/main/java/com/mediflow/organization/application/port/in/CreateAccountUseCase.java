package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

public interface CreateAccountUseCase {

    default CreationResult executeWithIdempotency(UUID actorAccountId, String idempotencyKey,
            String username, String rawPassword, UUID staffId, UUID patientId, Role role) {
        throw new UnsupportedOperationException("Account creation receipts are not configured");
    }

    record CreationResult(Account account, boolean replayed) {}

    default Account execute(
            String username,
            String rawPassword,
            UUID staffId,
            Role role) {
        return execute(username, rawPassword, staffId, null, role);
    }

    Account execute(
            String username,
            String rawPassword,
            UUID staffId,
            UUID patientId,
            Role role);
}
