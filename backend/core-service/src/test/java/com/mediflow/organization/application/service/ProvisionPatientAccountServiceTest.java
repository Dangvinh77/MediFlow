package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvisionPatientAccountServiceTest {
    final AccountRepository accounts = mock(AccountRepository.class);
    final AccountCreationReceiptRepository receipts = mock(AccountCreationReceiptRepository.class);
    final CreateAccountUseCase creator = mock(CreateAccountUseCase.class);
    final PatientAccountIdentityPort patients = mock(PatientAccountIdentityPort.class);
    final UUID patient = UUID.randomUUID(), actor = UUID.randomUUID();

    @Test void derivesCredentialsAndForcesPatientRole() {
        when(patients.identityNumber(patient)).thenReturn("012345678901");
        var account = Account.create(UUID.randomUUID(), "012345678901", "hash", null, patient, Role.PATIENT);
        account.requirePasswordChange();
        when(creator.executeWithIdempotency(actor, "key", "012345678901", "1", null, patient, Role.PATIENT))
                .thenReturn(new CreateAccountUseCase.CreationResult(account, false));
        var result = new ProvisionPatientAccountService(accounts, receipts, creator, patients).provision(actor, "key", patient);
        assertThat(result.account().getPatientId()).isEqualTo(patient);
        assertThat(result.account().isMustChangePassword()).isTrue();
        var order = inOrder(receipts, accounts, creator);
        order.verify(receipts).lockKey(actor, "key");
        order.verify(accounts).lockOwner(null, patient);
        order.verify(creator).executeWithIdempotency(actor, "key", "012345678901", "1", null, patient, Role.PATIENT);
    }

    @Test void refusesReissueEvenForAnInactiveExistingAccount() {
        when(patients.identityNumber(patient)).thenReturn("012345678901");
        when(accounts.existsByPatientId(patient)).thenReturn(true);
        assertThatThrownBy(() -> new ProvisionPatientAccountService(accounts, receipts, creator, patients)
                .provision(actor, "key", patient)).hasMessageContaining("tài khoản");
        verifyNoInteractions(creator);
    }

    @Test void allowsReceiptRecoveryWithoutIssuingAnotherAccount() {
        var account = Account.create(UUID.randomUUID(), "012345678901", "hash", null, patient, Role.PATIENT);
        when(receipts.find(actor, "key")).thenReturn(Optional.of(new AccountCreationReceiptRepository.Receipt(
                actor, "key", "fingerprint", "hash", account.getAccountId())));
        when(accounts.findById(account.getAccountId())).thenReturn(Optional.of(account));
        new ProvisionPatientAccountService(accounts, receipts, creator, patients).provision(actor, "key", patient);
        verifyNoInteractions(patients);
        verify(accounts, never()).existsByPatientId(any());
        verify(creator).executeWithIdempotency(actor, "key", "012345678901", "1", null, patient, Role.PATIENT);
    }

    @Test void unavailableOrInvalidPatientNeverCreatesAccount() {
        when(patients.identityNumber(patient)).thenThrow(new IllegalArgumentException("Patient unavailable"));
        assertThatThrownBy(() -> new ProvisionPatientAccountService(accounts, receipts, creator, patients)
                .provision(actor, "key", patient)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(creator);
    }
}
