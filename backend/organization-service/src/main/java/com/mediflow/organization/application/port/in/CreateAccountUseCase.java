package com.mediflow.organization.application.port.in;

import java.util.UUID;

import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

public interface CreateAccountUseCase {

    Account execute(
            String username,
            String rawPassword,
            UUID staffId,
            Role role);
}
