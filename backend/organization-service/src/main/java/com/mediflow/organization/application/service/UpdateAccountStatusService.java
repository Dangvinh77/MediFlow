package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.model.Account;

public class UpdateAccountStatusService implements UpdateAccountStatusUseCase {

    private final AccountRepository accountRepository;

    public UpdateAccountStatusService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Account execute(UUID accountId, boolean active) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (active && !account.isActive()) {
            account.activate();
        } else if (!active && account.isActive()) {
            account.deactivate();
        }

        return accountRepository.save(account);
    }
}
