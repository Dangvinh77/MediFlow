package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

@ExtendWith(MockitoExtension.class)
class CreateAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private CreateAccountService service;

    @BeforeEach
    void setUp() {
        service = new CreateAccountService(
                accountRepository,
                passwordHasher);
    }

    @Test
    void execute_validData_savesHashedPassword() {
        UUID staffId = UUID.randomUUID();
        when(accountRepository.existsByUsername("doctor.one"))
                .thenReturn(false);
        when(passwordHasher.hash("plainpass"))
                .thenReturn("$2a$10$hashed-value");
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account saved = service.execute(
                "doctor.one",
                "plainpass",
                staffId,
                Role.DOCTOR);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());

        Account savedArgument = captor.getValue();
        assertEquals(saved.getAccountId(), savedArgument.getAccountId());
        assertEquals("doctor.one", saved.getUsername());
        assertEquals("$2a$10$hashed-value", saved.getPasswordHash());
        assertNotEquals("plainpass", saved.getPasswordHash());
    }

    @Test
    void execute_duplicateUsername_throwsException() {
        when(accountRepository.existsByUsername("doctor.one"))
                .thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> service.execute(
                        "doctor.one",
                        "plainpass",
                        UUID.randomUUID(),
                        Role.DOCTOR));
    }

    @Test
    void execute_shortPassword_throwsInvalidAccountException() {
        when(accountRepository.existsByUsername("doctor.one"))
                .thenReturn(false);

        assertThrows(
                InvalidAccountException.class,
                () -> service.execute(
                        "doctor.one",
                        "short",
                        UUID.randomUUID(),
                        Role.DOCTOR));
    }

    @Test
    void execute_patientWithStaffId_throwsInvalidAccountException() {
        when(accountRepository.existsByUsername("patient.one")).thenReturn(false);

        assertThrows(InvalidAccountException.class, () -> service.execute(
                "patient.one", "plainpass", UUID.randomUUID(), Role.PATIENT));
    }

    @Test
    void execute_patientWithoutPatientId_throwsInvalidAccountException() {
        when(accountRepository.existsByUsername("patient.one")).thenReturn(false);

        assertThrows(InvalidAccountException.class, () -> service.execute(
                "patient.one", "plainpass", null, null, Role.PATIENT));
    }

    @Test
    void execute_patientWithoutStaffId_isValid() {
        when(accountRepository.existsByUsername("patient.one")).thenReturn(false);
        when(passwordHasher.hash("plainpass")).thenReturn("$2a$10$hashed-value");
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID patientId = UUID.randomUUID();
        Account account = service.execute("patient.one", "plainpass", null, patientId, Role.PATIENT);

        assertEquals(Role.PATIENT, account.getRole());
        org.junit.jupiter.api.Assertions.assertNull(account.getStaffId());
        assertEquals(patientId, account.getPatientId());
    }

    @Test
    void execute_systemAccount_isRejected() {
        assertThrows(InvalidAccountException.class,
                () -> service.execute("system", "plainpass", null, null, Role.SYSTEM));
    }

    @Test
    void execute_staffRoleWithoutStaffId_throwsInvalidAccountException() {
        when(accountRepository.existsByUsername("doctor.one")).thenReturn(false);

        assertThrows(InvalidAccountException.class, () -> service.execute(
                "doctor.one", "plainpass", null, Role.DOCTOR));
    }
}
