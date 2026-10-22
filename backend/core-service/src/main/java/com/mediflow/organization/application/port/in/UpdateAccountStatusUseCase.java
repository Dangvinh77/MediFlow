package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Account;

import java.util.UUID;

public interface UpdateAccountStatusUseCase {
    default Account execute(UUID accountId, boolean active, boolean replaceActiveAccount) {
        if (replaceActiveAccount) throw new UnsupportedOperationException("Account replacement is not configured");
        return execute(accountId, active);
    }

    Account execute(UUID accountId, boolean active);
}
