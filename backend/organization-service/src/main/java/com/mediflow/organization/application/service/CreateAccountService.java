package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class CreateAccountService implements CreateAccountUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    public CreateAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Account execute(
            String username,
            String rawPassword,
            UUID staffId,
            Role role) {

        if (accountRepository.existsByUsername(username)) {
            throw new DuplicateResourceException(
                    "ACCOUNT_USERNAME_DUPLICATE",
                    "Account username already exists: " + username);
        }

        validateRawPassword(rawPassword);

        String passwordHash = passwordHasher.hash(rawPassword);

        Account account = Account.create(
                UUID.randomUUID(),
                username,
                passwordHash,
                staffId,
                role);

        return accountRepository.save(account);
    }

    private void validateRawPassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidAccountException(
                    "Password must contain 8 to 72 characters");
        }
    }
}
