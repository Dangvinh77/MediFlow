package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.model.Account;
import org.springframework.transaction.annotation.Transactional;

@Transactional("organizationTransactionManager")
public class UpdateAccountStatusService implements UpdateAccountStatusUseCase {

    private final AccountRepository accountRepository;

    public UpdateAccountStatusService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Account execute(UUID accountId, boolean active) {
        return execute(accountId, active, false);
    }

    @Override
    public Account execute(UUID accountId, boolean active, boolean replaceActiveAccount) {
        // Owner lock must precede row locks, including when switching two accounts concurrently.
        Account snapshot = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        accountRepository.lockOwner(snapshot.getStaffId(), snapshot.getPatientId());
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (active) AccountOwnerPolicy.check(accountRepository, account, replaceActiveAccount);
        boolean changed = active != account.isActive();
        if (active && !account.isActive()) {
            account.activate();
        } else if (!active && account.isActive()) {
            account.deactivate();
        }

        Account saved = accountRepository.save(account);
        if (changed) accountRepository.incrementCredentialVersion(accountId);
        return saved;
    }
}
