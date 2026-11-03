package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.InvalidCredentialsException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class VerifyCredentialsService implements VerifyCredentialsUseCase {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid username or password";

    private final AccountRepository accountRepository;
    private final StaffRepository staffRepository;
    private final PasswordHasher passwordHasher;

    public VerifyCredentialsService(
            AccountRepository accountRepository,
            StaffRepository staffRepository,
            PasswordHasher passwordHasher) {
        this.accountRepository = accountRepository;
        this.staffRepository = staffRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public VerifiedAccount execute(String username, String rawPassword) {
        String email = normalizeEmail(username);
        Account account = accountRepository.findByUsernameForUpdate(email)
                .orElseThrow(() -> invalidCredentials());

        if (account.getRole() == Role.SYSTEM
                || !account.isActive()
                || !account.isEmailLoginEnabled()
                || (account.isMustChangePassword() && account.getTemporaryPasswordExpiresAt() != null
                    && !account.getTemporaryPasswordExpiresAt().isAfter(java.time.Instant.now()))
                || rawPassword == null
                || !passwordHasher.matches(rawPassword, account.getPasswordHash())) {
            throw invalidCredentials();
        }

        UUID departmentId = null;
        if (account.getStaffId() != null) {
            departmentId = staffRepository.findById(account.getStaffId())
                    .map(staff -> staff.getDepartmentId())
                    .orElse(null);
        }

        if ("1".equals(rawPassword)) account.requirePasswordChange();
        account.recordLogin();
        accountRepository.save(account);

        return new VerifiedAccount(
                account.getAccountId(),
                account.getStaffId(),
                departmentId,
                account.getPatientId(),
                account.getRole(),
                accountRepository.credentialVersion(account.getAccountId()), account.isMustChangePassword());
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }

    private String normalizeEmail(String value) {
        if (value == null || !value.equals(value.trim()) || value.length() > 254
                || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw invalidCredentials();
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
