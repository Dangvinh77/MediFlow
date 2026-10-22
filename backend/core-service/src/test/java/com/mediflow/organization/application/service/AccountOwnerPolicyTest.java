package com.mediflow.organization.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountOwnerPolicyTest {
    @Mock AccountRepository accounts;
    @Mock PasswordHasher passwords;

    @Test void creationConflictDoesNotChangeExistingAccount() {
        UUID staff = UUID.randomUUID();
        Account previous = Account.create(UUID.randomUUID(), "previous", "hash", staff, null, Role.DOCTOR);
        when(passwords.hash("new-password")).thenReturn("new-hash");
        when(accounts.findActiveByOwner(staff, null)).thenReturn(List.of(previous));
        var service = new CreateAccountService(accounts, passwords);
        var conflict = assertThrows(DuplicateResourceException.class,
                () -> service.execute("second", "new-password", staff, null, Role.DOCTOR));
        assertThat(conflict.getCode()).isEqualTo("ACCOUNT_OWNER_ACTIVE");
        assertThat(conflict.getMessage()).contains("previous");
        assertThat(previous.isActive()).isTrue();
        verify(accounts, never()).save(any());
        verify(accounts, never()).incrementCredentialVersion(any());
        var order = inOrder(accounts);
        order.verify(accounts).lockOwner(staff, null);
        order.verify(accounts).findActiveByOwner(staff, null);
    }

    @Test void confirmedPatientCreationDeactivatesAllOldAccountsAndRevokesCredentials() {
        UUID patient = UUID.randomUUID();
        Account first = Account.create(UUID.randomUUID(), "first", "old-hash", null, patient, Role.PATIENT);
        Account second = Account.create(UUID.randomUUID(), "second", "old-hash", null, patient, Role.PATIENT);
        when(passwords.hash("new-password")).thenReturn("new-hash");
        when(accounts.findActiveByOwner(null, patient)).thenReturn(List.of(first, second));
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        var created = new CreateAccountService(accounts, passwords)
                .execute("third", "new-password", null, patient, Role.PATIENT, true);
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        assertThat(first.getPasswordHash()).isEqualTo("old-hash");
        assertThat(created.isActive()).isTrue();
        var order = inOrder(accounts);
        order.verify(accounts).lockOwner(null, patient);
        order.verify(accounts).findActiveByOwner(null, patient);
        order.verify(accounts).save(first);
        order.verify(accounts).incrementCredentialVersion(first.getAccountId());
        order.verify(accounts).save(second);
        order.verify(accounts).incrementCredentialVersion(second.getAccountId());
        order.verify(accounts).save(created);
    }

    @Test void reactivationAlsoRequiresConfirmationAndLocksOwnerBeforeAccountRow() {
        UUID staff = UUID.randomUUID();
        Account target = Account.create(UUID.randomUUID(), "target", "hash", staff, null, Role.ADMIN);
        target.deactivate();
        Account previous = Account.create(UUID.randomUUID(), "previous", "hash", staff, null, Role.ADMIN);
        when(accounts.findById(target.getAccountId())).thenReturn(Optional.of(target));
        when(accounts.findByIdForUpdate(target.getAccountId())).thenReturn(Optional.of(target));
        when(accounts.findActiveByOwner(staff, null)).thenReturn(List.of(previous));
        var service = new UpdateAccountStatusService(accounts);
        assertThrows(DuplicateResourceException.class, () -> service.execute(target.getAccountId(), true));
        assertThat(target.isActive()).isFalse();
        assertThat(previous.isActive()).isTrue();
        verify(accounts, never()).save(any());
        var order = inOrder(accounts);
        order.verify(accounts).lockOwner(staff, null);
        order.verify(accounts).findByIdForUpdate(target.getAccountId());

        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        service.execute(target.getAccountId(), true, true);
        assertThat(target.isActive()).isTrue();
        assertThat(previous.isActive()).isFalse();
        verify(accounts).incrementCredentialVersion(previous.getAccountId());
        verify(accounts).incrementCredentialVersion(target.getAccountId());
    }

    @Test void activeAccountDoesNotConflictWithItself() {
        var target = Account.create(UUID.randomUUID(), "same", "hash", UUID.randomUUID(), null, Role.NURSE);
        when(accounts.findActiveByOwner(target.getStaffId(), null)).thenReturn(List.of(target));
        AccountOwnerPolicy.check(accounts, target, false);
        verify(accounts, never()).save(any());
        assertThat(target.isActive()).isTrue();
    }
}
