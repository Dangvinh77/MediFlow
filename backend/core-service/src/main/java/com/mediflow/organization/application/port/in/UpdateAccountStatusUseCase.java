package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Account;

import java.util.UUID;

public interface UpdateAccountStatusUseCase {

    Account execute(UUID accountId, boolean active);
}
