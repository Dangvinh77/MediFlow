package com.mediflow.organization.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.organization.domain.model.Account;

public interface AccountRepository {

    boolean existsByUsername(String username);

    Optional<Account> findById(UUID accountId);

    Optional<Account> findByUsername(String username);

    Account save(Account account);
}
