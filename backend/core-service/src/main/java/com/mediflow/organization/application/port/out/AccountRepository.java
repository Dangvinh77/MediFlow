package com.mediflow.organization.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.organization.domain.model.Account;

public interface AccountRepository {

    void lockOwner(UUID staffId, UUID patientId);

    java.util.List<Account> findActiveByOwner(UUID staffId, UUID patientId);

    boolean existsByUsername(String username);

    Optional<Account> findById(UUID accountId);

    Optional<Account> findByUsername(String username);

    Optional<Account> findByIdForUpdate(UUID accountId);

    Optional<Account> findByUsernameForUpdate(String username);

    com.mediflow.common.api.PageResult<Account> search(String keyword,
            com.mediflow.organization.domain.model.Role role, Boolean active,
            com.mediflow.common.api.PageQuery page);

    long credentialVersion(UUID accountId);

    void incrementCredentialVersion(UUID accountId);

    Account save(Account account);
}
