package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountEmailChangeStore;
import com.mediflow.organization.application.port.out.AccountRecoveryTokenStore;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SecurityAccountOperationsServiceTest {
    private final CreateAccountUseCase creator = mock(CreateAccountUseCase.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final PatientAccountIdentityPort patients = mock(PatientAccountIdentityPort.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final SecurityMailStore mail = mock(SecurityMailStore.class);
    private final FakeRecoveryTokens recovery = new FakeRecoveryTokens();
    private final AccountEmailChangeStore emailChanges = mock(AccountEmailChangeStore.class);
    private final SecurityAccountOperationsService service = new SecurityAccountOperationsService(
            creator, accounts, staff, patients, passwords, mail, recovery, emailChanges,
            new SecurityMailTemplate("http://localhost:3000"), "no-reply@example.test");

    @BeforeEach
    void setUp() {
        reset(creator, accounts, staff, patients, passwords, mail, emailChanges);
        recovery.clear();
    }

    @Test
    void forgotPasswordUsesGenericAckForUnknownAndEmailChangedAfterLookup() {
        when(accounts.findByUsername("missing@example.test")).thenReturn(Optional.empty());
        assertThat(service.forgotPassword("missing@example.test").accepted()).isTrue();

        Account previouslyMatched = account("old@example.test", true);
        Account nowCorrected = account("new@example.test", true);
        when(accounts.findByUsername("old@example.test")).thenReturn(Optional.of(previouslyMatched));
        when(accounts.findByIdForUpdate(previouslyMatched.getAccountId())).thenReturn(Optional.of(nowCorrected));

        assertThat(service.forgotPassword("old@example.test").accepted()).isTrue();
        assertThat(recovery.token).isNull();
        verify(mail, never()).enqueue(any());
    }

    @Test
    void recoveryLinkExpiresAndCanOnlyBeConsumedOnce() {
        UUID id = UUID.randomUUID();
        Account account = account("person@example.test", true);
        when(accounts.findByIdForUpdate(account.getAccountId())).thenReturn(Optional.of(account));
        when(accounts.credentialVersion(account.getAccountId())).thenReturn(7L, 8L);
        when(passwords.matches("chosen-pass-1", account.getPasswordHash())).thenReturn(false);
        when(passwords.hash("chosen-pass-1")).thenReturn("new-hash");
        when(accounts.save(account)).thenReturn(account);
        when(mail.enqueue(any())).thenReturn(new MailDeliveryDTO(UUID.randomUUID(), MailDeliveryDTO.Status.SENT));

        recovery.put(new AccountRecoveryTokenStore.RecoveryToken(id, account.getAccountId(), hash("opaque-token"),
                7L, Instant.now().plusSeconds(60), null));
        assertThat(service.resetPassword("opaque-token", "chosen-pass-1").success()).isTrue();
        assertThatThrownBy(() -> service.resetPassword("opaque-token", "chosen-pass-1"))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("expired, or already used");

        recovery.put(new AccountRecoveryTokenStore.RecoveryToken(UUID.randomUUID(), account.getAccountId(),
                hash("expired-token"), 8L, Instant.now().minusSeconds(1), null));
        assertThatThrownBy(() -> service.resetPassword("expired-token", "another-pass-1"))
                .isInstanceOf(InvalidAccountException.class);
    }

    @Test
    void staffRoleMustMatchPersistedJobTitle() {
        UUID actor = UUID.randomUUID();
        Staff owner = Staff.create("Dr Test", UUID.randomUUID(), JobTitle.DOCTOR,
                "Internal", "LIC-1", null, "doctor@example.test", "012345678901");
        UUID staffId = owner.getStaffId();
        when(staff.findById(staffId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.issueStaff(actor, "key", "doctor@example.test", staffId,
                Role.ADMIN, false)).isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(creator);
    }

    private static Account account(String email, boolean active) {
        Account account = Account.create(UUID.randomUUID(), email, "old-hash", null,
                UUID.randomUUID(), Role.PATIENT);
        if (!active) account.deactivate();
        return account;
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }

    private static final class FakeRecoveryTokens implements AccountRecoveryTokenStore {
        private RecoveryToken token;
        void clear() { token = null; }
        void put(RecoveryToken value) { token = value; }

        @Override public void invalidateUnused(UUID accountId, Instant at) {
            if (token != null && token.accountId().equals(accountId) && token.usedAt() == null) {
                token = new RecoveryToken(token.tokenId(), token.accountId(), token.tokenHash(),
                        token.credentialVersion(), token.expiresAt(), at);
            }
        }
        @Override public void save(UUID tokenId, UUID accountId, String tokenHash, long version, Instant expiresAt) {
            token = new RecoveryToken(tokenId, accountId, tokenHash, version, expiresAt, null);
        }
        @Override public Optional<RecoveryToken> find(String hash) {
            return token != null && token.tokenHash().equals(hash) ? Optional.of(token) : Optional.empty();
        }
        @Override public Optional<RecoveryToken> findForUpdate(String hash) { return find(hash); }
        @Override public boolean markUsed(UUID tokenId, Instant at) {
            if (token == null || !token.tokenId().equals(tokenId) || token.usedAt() != null) return false;
            token = new RecoveryToken(token.tokenId(), token.accountId(), token.tokenHash(),
                    token.credentialVersion(), token.expiresAt(), at);
            return true;
        }
    }
}
