package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

@ExtendWith(MockitoExtension.class)
class ManageAccountsServiceTest {
    @Mock AccountRepository accounts;
    @Mock PasswordHasher passwords;
    ManageAccountsService service;
    Account account;
    @BeforeEach void setup() {
        service = new ManageAccountsService(accounts, passwords);
        account = Account.create(UUID.randomUUID(), "doctor.one", "old-hash", UUID.randomUUID(), Role.DOCTOR);
        when(accounts.findByIdForUpdate(account.getAccountId())).thenReturn(Optional.of(account));
    }
    @Test void wrongCurrentPasswordDoesNotWrite() {
        assertThrows(InvalidAccountException.class, () -> service.changePassword(account.getAccountId(), "wrong", "new-password"));
        verify(accounts, never()).save(any());
        verify(passwords, never()).hash(any());
    }
    @Test void inactiveAccountCannotChangePassword() {
        account.deactivate();
        assertThrows(InvalidAccountException.class, () -> service.changePassword(account.getAccountId(), "old", "new-password"));
        verifyNoInteractions(passwords);
    }
    @Test void successfulChangeHashesAndSavesOnceWithoutChangingRoleOrStatus() {
        when(passwords.matches("old-password", "old-hash")).thenReturn(true);
        when(passwords.hash("new-password")).thenReturn("new-hash");
        service.changePassword(account.getAccountId(), "old-password", "new-password");
        assertEquals("new-hash", account.getPasswordHash());
        assertEquals(Role.DOCTOR, account.getRole());
        assertTrue(account.isActive());
        verify(accounts).save(account);
    }
    @Test void rejectsBcryptTruncationAndSamePassword() {
        assertThrows(InvalidAccountException.class, () -> service.resetPassword(account.getAccountId(), "ệ".repeat(25)));
        when(passwords.matches("same-password", "old-hash")).thenReturn(true);
        assertThrows(InvalidAccountException.class, () -> service.resetPassword(account.getAccountId(), "same-password"));
        verify(accounts, never()).save(any());
    }
    @Test void adminResetDoesNotRequireOldPasswordOrReactivateAccount() {
        account.deactivate();
        when(passwords.hash("new-password")).thenReturn("reset-hash");
        service.resetPassword(account.getAccountId(), "new-password");
        assertFalse(account.isActive());
        assertEquals("reset-hash", account.getPasswordHash());
        verify(accounts).save(account);
    }
}
