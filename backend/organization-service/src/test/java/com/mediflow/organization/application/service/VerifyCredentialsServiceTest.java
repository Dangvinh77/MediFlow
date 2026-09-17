package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase.VerifiedAccount;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.InvalidCredentialsException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.model.Staff;

@ExtendWith(MockitoExtension.class)
class VerifyCredentialsServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private VerifyCredentialsService service;

    @BeforeEach
    void setUp() {
        service = new VerifyCredentialsService(
                accountRepository,
                staffRepository,
                passwordHasher);
    }

    @Test
    void execute_validCredentials_returnsIdentityAndRecordsLogin() {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Account account = account(staffId, true);
        Staff staff = Staff.create(
                "Alex Morgan",
                departmentId,
                JobTitle.DOCTOR,
                "Internal Medicine",
                "MED123",
                "0901234567",
                "alex@example.com");

        when(accountRepository.findByUsername("doctor.one"))
                .thenReturn(Optional.of(account));
        when(passwordHasher.matches("plainpass", account.getPasswordHash()))
                .thenReturn(true);
        when(staffRepository.findById(staffId))
                .thenReturn(Optional.of(staff));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VerifiedAccount result = service.execute("doctor.one", "plainpass");

        assertEquals(account.getAccountId(), result.accountId());
        assertEquals(staffId, result.staffId());
        assertEquals(departmentId, result.departmentId());
        assertEquals(Role.DOCTOR, result.role());
        verify(accountRepository).save(account);
    }

    @Test
    void execute_patientCredentials_returnsNullDepartment() {
        Account account = account(null, true, Role.PATIENT);
        when(accountRepository.findByUsername("patient.one"))
                .thenReturn(Optional.of(account));
        when(passwordHasher.matches("plainpass", account.getPasswordHash()))
                .thenReturn(true);
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VerifiedAccount result = service.execute("patient.one", "plainpass");

        assertNull(result.departmentId());
    }

    @Test
    void execute_unknownAndWrongPassword_useSameError() {
        when(accountRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());
        InvalidCredentialsException unknown = assertThrows(
                InvalidCredentialsException.class,
                () -> service.execute("unknown", "plainpass"));

        Account account = account(UUID.randomUUID(), true);
        when(accountRepository.findByUsername("doctor.one"))
                .thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrongpass", account.getPasswordHash()))
                .thenReturn(false);
        InvalidCredentialsException wrongPassword = assertThrows(
                InvalidCredentialsException.class,
                () -> service.execute("doctor.one", "wrongpass"));

        assertEquals(unknown.getMessage(), wrongPassword.getMessage());
    }

    @Test
    void execute_disabledAccount_usesSameErrorAndDoesNotCheckPassword() {
        Account account = account(UUID.randomUUID(), false);
        when(accountRepository.findByUsername("doctor.one"))
                .thenReturn(Optional.of(account));

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> service.execute("doctor.one", "plainpass"));

        assertEquals("Invalid username or password", exception.getMessage());
        verify(passwordHasher, never()).matches(any(), any());
    }

    private Account account(UUID staffId, boolean active) {
        return account(staffId, active, Role.DOCTOR);
    }

    private Account account(UUID staffId, boolean active, Role role) {
        return new Account(
                UUID.randomUUID(),
                role == Role.PATIENT ? "patient.one" : "doctor.one",
                "$2a$10$hashed-value",
                staffId,
                role,
                active,
                null,
                java.time.Instant.now(),
                java.time.Instant.now());
    }
}
