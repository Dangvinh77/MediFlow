package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountEmailChangeStore;
import com.mediflow.organization.application.port.out.AccountRecoveryTokenStore;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountSecurityAuditStore;
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
import org.mockito.ArgumentCaptor;

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
    private final PatientAccountSecurityAuditStore patientSecurityAudit = mock(PatientAccountSecurityAuditStore.class);
    private final SecurityAccountOperationsService service = new SecurityAccountOperationsService(
            creator, accounts, staff, patients, passwords, mail, recovery, emailChanges, patientSecurityAudit,
            new SecurityMailTemplate("http://localhost:3000"), "no-reply@example.test");

    @BeforeEach
    void setUp() {
        reset(creator, accounts, staff, patients, passwords, mail, emailChanges, patientSecurityAudit);
        recovery.clear();
    }

    @Test
    void staffIssuanceChecksActivityAfterTakingTheSameOwnerLockAsDeactivation() {
        Staff owner = Staff.create("Former staff", UUID.randomUUID(), JobTitle.DOCTOR,
                null, "LIC", null, "former@example.test", "012345678901");
        owner.deactivate();
        when(staff.findById(owner.getStaffId())).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.issueStaff(UUID.randomUUID(), "key", owner.getEmail(), owner.getStaffId(), Role.DOCTOR, false))
                .isInstanceOf(InvalidAccountException.class);
        var order = inOrder(accounts, staff);
        order.verify(accounts).lockOwner(owner.getStaffId(), null);
        order.verify(staff).findById(owner.getStaffId());
        verifyNoInteractions(creator);
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

    @Test
    void patientScopedResetVerifiesPatientEmailAndWritesActorTargetAuditAtomically() {
        UUID actor = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "patient@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        MailDeliveryDTO delivery = new MailDeliveryDTO(UUID.randomUUID(), MailDeliveryDTO.Status.PENDING);
        when(patients.email(patientId)).thenReturn("patient@example.test");
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(patients.profileEmail(patientId)).thenReturn("same@example.test");
        when(passwords.hash(anyString())).thenReturn("new-hash");
        when(accounts.save(target)).thenReturn(target);
        when(mail.enqueue(any())).thenReturn(delivery);

        MailDeliveryDTO result = service.resetPatientPassword(actor, patientId, "patient-reset-key", correlationId);

        assertThat(result).isEqualTo(delivery);
        assertThat(target.getPasswordHash()).isEqualTo("new-hash");
        verify(accounts).incrementCredentialVersion(target.getAccountId());
        verify(patientSecurityAudit).record(
                eq(PatientAccountSecurityAuditStore.Action.PATIENT_PASSWORD_RESET), eq(actor), eq(patientId),
                eq(target.getAccountId()), eq(correlationId), argThat(key -> key.startsWith("patient-reset:" + patientId + ":")));
        verify(mail).enqueue(argThat(message -> message.accountId().equals(target.getAccountId())
                && message.recipient().equals("patient@example.test")
                && message.kind().equals("ADMIN_RESET")));
    }

    @Test
    void patientResetReplayReturnsOriginalDeliveryWithoutChangingCredentialAgain() {
        UUID actor = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        MailDeliveryDTO original = new MailDeliveryDTO(UUID.randomUUID(), MailDeliveryDTO.Status.SENT);
        when(mail.byOperationKey(anyString())).thenReturn(Optional.of(original));

        assertThat(service.resetPatientPassword(actor, patientId, "same-key", UUID.randomUUID())).isEqualTo(original);

        verify(mail).lockOperation(anyString());
        verify(mail).byOperationKey(anyString());
        verifyNoInteractions(patients, accounts, passwords, patientSecurityAudit);
    }

    @Test
    void patientResetRejectsEmailSyncPendingOrIneligibleProfilesBeforeChangingCredential() {
        UUID actor = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "old@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        when(patients.email(patientId)).thenReturn("new@example.test");
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.resetPatientPassword(actor, patientId, "key", UUID.randomUUID()))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("profile email");

        assertThat(target.getPasswordHash()).isEqualTo("old-hash");
        verify(mail, never()).enqueue(any());
        verifyNoInteractions(patientSecurityAudit);
    }

    @Test
    void patientResetRejectsInactiveLinkedAccount() {
        UUID patientId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "patient@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        target.deactivate();
        when(patients.email(patientId)).thenReturn("patient@example.test");
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.resetPatientPassword(UUID.randomUUID(), patientId, "key", UUID.randomUUID()))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("active patient email account");

        verify(mail, never()).enqueue(any());
        verifyNoInteractions(patientSecurityAudit);
    }

    @Test
    void patientMailRetryIsOwnerScopedEmailSynchronizedAndAudited() {
        UUID actor = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "patient@example.test", "hash", null,
                patientId, Role.PATIENT);
        MailDeliveryDTO retried = new MailDeliveryDTO(messageId, MailDeliveryDTO.Status.PENDING);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(patients.email(patientId)).thenReturn("patient@example.test");
        when(mail.retry(target.getAccountId(), messageId, target.getUsername())).thenReturn(retried);

        assertThat(service.retryPatientDelivery(actor, patientId, messageId, correlationId)).isEqualTo(retried);

        verify(mail).retry(target.getAccountId(), messageId, "patient@example.test");
        verify(patientSecurityAudit).record(PatientAccountSecurityAuditStore.Action.PATIENT_MAIL_RETRY,
                actor, patientId, target.getAccountId(), correlationId, null);
    }

    @Test
    void patientMailRetryRejectsMessagesSentToAnOldProfileEmail() {
        UUID patientId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "old@example.test", "hash", null,
                patientId, Role.PATIENT);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(patients.email(patientId)).thenReturn("current@example.test");

        assertThatThrownBy(() -> service.retryPatientDelivery(UUID.randomUUID(), patientId,
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("profile email");

        verify(mail, never()).retry(any(), any(), anyString());
        verifyNoInteractions(patientSecurityAudit);
    }

    @Test
    void patientScopedEmailChangeUsesPatientOwnerAndAuditedCredentialSyncFlow() {
        UUID actor = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "old@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        recovery.put(new AccountRecoveryTokenStore.RecoveryToken(UUID.randomUUID(), target.getAccountId(),
                hash("old-recovery"), 3L, Instant.now().plusSeconds(300), null));
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(accounts.existsByEmail("new@example.test")).thenReturn(false);
        when(patients.emailAvailable(patientId, "new@example.test")).thenReturn(true);
        when(accounts.save(target)).thenReturn(target);
        when(accounts.credentialVersion(target.getAccountId())).thenReturn(4L);
        when(patients.displayName(patientId)).thenReturn("Patient One");

        Account updated = service.changePatientEmail(actor, patientId, "NEW@example.test", "  corrected at registration  ");

        assertThat(updated.getUsername()).isEqualTo("new@example.test");
        verify(patients).requireActivePatient(patientId);
        verify(accounts).findByOwner(null, patientId);
        verify(accounts).incrementCredentialVersion(target.getAccountId());
        verify(emailChanges).record(target.getAccountId(), actor, "old@example.test", "new@example.test",
                "corrected at registration");
        verify(emailChanges).queuePatientProfileSync(target.getAccountId(), patientId, "new@example.test");
        assertThat(recovery.token.usedAt()).isNotNull();

        ArgumentCaptor<SecurityMailStore.NewMessage> messages = ArgumentCaptor.forClass(SecurityMailStore.NewMessage.class);
        verify(mail, times(2)).enqueue(messages.capture());
        assertThat(messages.getAllValues()).extracting(SecurityMailStore.NewMessage::recipient)
                .containsExactlyInAnyOrder("old@example.test", "new@example.test");
        assertThat(messages.getAllValues()).allMatch(message -> "ACCOUNT_EMAIL_CHANGED".equals(message.kind()));
    }

    @Test
    void patientScopedEmailChangeRejectsAnotherPatientAccountOwner() {
        UUID patientId = UUID.randomUUID();
        Account wrongOwner = Account.create(UUID.randomUUID(), "other@example.test", "hash", null,
                UUID.randomUUID(), Role.PATIENT);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(wrongOwner));

        assertThatThrownBy(() -> service.changePatientEmail(UUID.randomUUID(), patientId,
                "new@example.test", "correction"))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("not linked to this patient profile");

        verify(accounts, never()).findByIdForUpdate(any());
        verify(accounts, never()).save(any());
        verifyNoInteractions(emailChanges, mail);
    }

    @Test
    void patientScopedEmailChangeRejectsStaffAccountReturnedForPatientOwner() {
        UUID patientId = UUID.randomUUID();
        Account staffAccount = Account.create(UUID.randomUUID(), "staff@example.test", "hash",
                UUID.randomUUID(), null, Role.NURSE);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(staffAccount));

        assertThatThrownBy(() -> service.changePatientEmail(UUID.randomUUID(), patientId,
                "new@example.test", "correction"))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("not linked to this patient profile");

        verify(accounts, never()).save(any());
        verifyNoInteractions(emailChanges, mail);
    }

    @Test
    void patientScopedEmailChangeRejectsArchivedProfileBeforeResolvingAccount() {
        UUID patientId = UUID.randomUUID();
        doThrow(new InvalidAccountException("archived patient profile"))
                .when(patients).requireActivePatient(patientId);

        assertThatThrownBy(() -> service.changePatientEmail(UUID.randomUUID(), patientId,
                "new@example.test", "correction"))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("archived patient profile");

        verify(accounts).lockOwner(null, patientId);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(emailChanges, mail);
    }

    @Test
    void patientScopedEmailChangeRejectsDuplicateEmailWithoutChangingCredentials() {
        UUID patientId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "old@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(accounts.existsByEmail("used@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.changePatientEmail(UUID.randomUUID(), patientId,
                "used@example.test", "correction"))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("already linked");

        verify(accounts, never()).save(any());
        verify(accounts, never()).incrementCredentialVersion(any());
        verifyNoInteractions(emailChanges, mail);
    }

    @Test
    void samePatientAccountEmailRequeuesOnlyWhenProfileSyncIsStillPending() {
        UUID patientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account target = Account.create(accountId, "same@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(target));
        when(patients.profileEmail(patientId)).thenReturn("stale@example.test");

        Account unchanged = service.changePatientEmail(UUID.randomUUID(), patientId,
                "same@example.test", "retry profile sync");

        assertThat(unchanged.getUsername()).isEqualTo("same@example.test");
        verify(emailChanges).queuePatientProfileSync(accountId, patientId, "same@example.test");
        verify(accounts, never()).save(any());
        verify(accounts, never()).incrementCredentialVersion(any());
        verifyNoInteractions(mail);
    }

    @Test
    void patientScopedEmailChangeWithSameAddressDoesNotRevokeOrSendAgain() {
        UUID patientId = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "same@example.test", "old-hash", null,
                patientId, Role.PATIENT);
        when(accounts.findByOwner(null, patientId)).thenReturn(java.util.List.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(patients.profileEmail(patientId)).thenReturn("same@example.test");

        Account unchanged = service.changePatientEmail(UUID.randomUUID(), patientId,
                "SAME@example.test", "correction");

        assertThat(unchanged.getUsername()).isEqualTo("same@example.test");
        verify(accounts, never()).save(any());
        verify(accounts, never()).incrementCredentialVersion(any());
        verifyNoInteractions(emailChanges, mail);
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
