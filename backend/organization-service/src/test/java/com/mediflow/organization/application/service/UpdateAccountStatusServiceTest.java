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

    @BeforeEach
    void setUp() {
        service = new UpdateAccountStatusService(accountRepository);
    }

    @Test
    void execute_false_deactivatesAccount() {
        Account account = account(false);
        when(accountRepository.findById(account.getAccountId()))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account result = service.execute(account.getAccountId(), false);

        assertFalse(result.isActive());
    }

    @Test
    void execute_true_activatesAccount() {
        Account account = account(false);
        when(accountRepository.findById(account.getAccountId()))
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
