package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

@ExtendWith(MockitoExtension.class)
class UpdateAccountStatusServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private UpdateAccountStatusService service;
    @Mock private com.mediflow.organization.application.port.out.StaffRepository staffRepository;

    @BeforeEach
    void setUp() {
        service = new UpdateAccountStatusService(accountRepository, staffRepository);
    }

    @Test
    void inactiveStaffCannotHaveAnAccountReactivated() {
        var staff = org.mockito.Mockito.mock(com.mediflow.organization.application.port.out.StaffRepository.class);
        var account = account(false);
        var owner = com.mediflow.organization.domain.model.Staff.create("Former staff", UUID.randomUUID(),
                com.mediflow.organization.domain.model.JobTitle.DOCTOR, null, "LIC", null, null, "012345678901");
        owner.deactivate();
        when(accountRepository.findById(account.getAccountId())).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(account.getAccountId())).thenReturn(Optional.of(account));
        when(staff.findById(account.getStaffId())).thenReturn(Optional.of(owner));
        assertThrows(com.mediflow.organization.domain.exception.InvalidAccountException.class,
                () -> new UpdateAccountStatusService(accountRepository, staff).execute(account.getAccountId(), true));
        org.mockito.Mockito.verify(accountRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void execute_false_deactivatesAccount() {
        Account account = account(false);
        when(accountRepository.findById(account.getAccountId())).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(account.getAccountId()))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account result = service.execute(account.getAccountId(), false);

        assertFalse(result.isActive());
    }

    @Test
    void execute_true_activatesAccount() {
        Account account = account(false);
        var owner = com.mediflow.organization.domain.model.Staff.create("Active staff", UUID.randomUUID(),
                com.mediflow.organization.domain.model.JobTitle.DOCTOR, null, "LIC", null, null, "012345678901");
        when(staffRepository.findById(account.getStaffId())).thenReturn(Optional.of(owner));
        when(accountRepository.findById(account.getAccountId())).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(account.getAccountId()))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account result = service.execute(account.getAccountId(), true);

        assertTrue(result.isActive());
    }

    @Test
    void execute_unknownAccount_throwsNotFoundException() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> service.execute(accountId, false));
    }

    private Account account(boolean active) {
        return new Account(
                UUID.randomUUID(),
                "doctor.one",
                "$2a$10$hashed-value",
                UUID.randomUUID(),
                Role.DOCTOR,
                active,
                null,
                Instant.now(),
                Instant.now());
    }
}
