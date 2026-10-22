package com.mediflow.organization.application.service;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository;
import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository.Receipt;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.springframework.transaction.annotation.Transactional;

@Transactional("organizationTransactionManager")
public class CreateAccountService implements CreateAccountUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final AccountCreationReceiptRepository receipts;

    public CreateAccountService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher) {
        this(accountRepository, passwordHasher, null);
    }

    public CreateAccountService(AccountRepository accountRepository, PasswordHasher passwordHasher,
            AccountCreationReceiptRepository receipts) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.receipts = receipts;
    }

    @Override
    public CreationResult executeWithIdempotency(UUID actorAccountId, String idempotencyKey,
            String username, String rawPassword, UUID staffId, UUID patientId, Role role) {
        return executeWithIdempotency(actorAccountId, idempotencyKey, username, rawPassword, staffId, patientId, role, false);
    }

    @Override
    public CreationResult executeWithIdempotency(UUID actorAccountId, String idempotencyKey,
            String username, String rawPassword, UUID staffId, UUID patientId, Role role, boolean replaceActiveAccount) {
        if (actorAccountId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Actor and idempotency key are required");
        }
        if (receipts == null) throw new IllegalStateException("Account receipt store is unavailable");
        receipts.lockKey(actorAccountId, idempotencyKey);
        String fingerprint = fingerprint(username, staffId, patientId, role, replaceActiveAccount);
        var existing = receipts.find(actorAccountId, idempotencyKey);
        if (existing.isPresent()) {
            Receipt receipt = existing.get();
            if (!receipt.requestFingerprint().equals(fingerprint)
                    || !passwordHasher.matches(rawPassword, receipt.passwordHash())) {
                throw new DuplicateResourceException("ACCOUNT_IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key was already used for another account request");
            }
            Account account = accountRepository.findById(receipt.accountId())
                    .orElseThrow(() -> new IllegalStateException("Account creation receipt target is missing"));
            return new CreationResult(account, true);
        }
        Account account = execute(username, rawPassword, staffId, patientId, role, replaceActiveAccount);
        receipts.save(new Receipt(actorAccountId, idempotencyKey, fingerprint,
                account.getPasswordHash(), account.getAccountId()));
        return new CreationResult(account, false);
    }

    private String fingerprint(String username, UUID staffId, UUID patientId, Role role, boolean replaceActiveAccount) {
        // Passwords are checked using the original salted hash, never a fast digest.
        StringBuilder canonical = new StringBuilder();
        for (String value : new String[] { username, staffId == null ? null : staffId.toString(),
                patientId == null ? null : patientId.toString(), role == null ? null : role.name() }) {
            canonical.append(value == null ? -1 : value.length()).append(':');
            if (value != null) canonical.append(value);
        }
        if (replaceActiveAccount) canonical.append(":replace-active");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    // Keep the legacy staff-only entry point on the concrete transactional bean.
    // An inherited interface default can bypass Spring's transaction advice.
    @Override
    public Account execute(String username, String rawPassword, UUID staffId, Role role) {
        return execute(username, rawPassword, staffId, null, role, false);
    }

    @Override
    public Account execute(
            String username,
            String rawPassword,
            UUID staffId,
            UUID patientId,
            Role role) {

        return execute(username, rawPassword, staffId, patientId, role, false);
    }

    @Override
    public Account execute(String username, String rawPassword, UUID staffId, UUID patientId,
            Role role, boolean replaceActiveAccount) {

        if (role == Role.SYSTEM) {
            throw new InvalidAccountException(
                    "SYSTEM accounts are issued only as short-lived service tokens");
        }

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
                patientId,
                role);

        AccountOwnerPolicy.check(accountRepository, account, replaceActiveAccount);
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
