package com.mediflow.organization.application.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.port.in.ManageAccountsUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.springframework.transaction.annotation.Transactional;

@Transactional("organizationTransactionManager")
public class ManageAccountsService implements ManageAccountsUseCase {
    private final AccountRepository accounts;
    private final PasswordHasher passwords;

    public ManageAccountsService(AccountRepository accounts, PasswordHasher passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Override
    @Transactional(value = "organizationTransactionManager", readOnly = true)
    public PageResult<Account> search(String keyword, Role role, Boolean active, PageQuery page) {
        return accounts.search(keyword, role, active, page);
    }

    @Override
    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        Account account = requireAccount(accountId);
        if (!account.isActive() || !passwords.matches(currentPassword, account.getPasswordHash())) {
            throw new InvalidAccountException("Current password is incorrect or account is inactive");
        }
        updatePassword(account, newPassword);
    }

    @Override
    public void resetPassword(UUID accountId, String newPassword) {
        Account account = requireAccount(accountId);
        if (account.getRole() == Role.SYSTEM) {
            throw new InvalidAccountException("Service accounts do not support password recovery");
        }
        updatePassword(account, newPassword);
    }

    private Account requireAccount(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private void updatePassword(Account account, String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAccountException("Password must have at least 8 characters and at most 72 UTF-8 bytes");
        }
        if (passwords.matches(password, account.getPasswordHash())) {
            throw new InvalidAccountException("New password must differ from the current password");
        }
        account.changePassword(passwords.hash(password));
        accounts.save(account);
        accounts.incrementCredentialVersion(account.getAccountId());
    }
}
