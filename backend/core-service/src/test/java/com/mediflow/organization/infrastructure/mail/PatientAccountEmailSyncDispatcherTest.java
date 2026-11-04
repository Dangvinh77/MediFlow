package com.mediflow.organization.infrastructure.mail;

import com.mediflow.organization.application.port.out.AccountEmailChangeStore;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PatientAccountEmailSyncDispatcherTest {
    @Test
    void staleBJobReadsCurrentAccountEmailAndCannotOverwriteNewerCValue() {
        var syncs = mock(AccountEmailChangeStore.class);
        var accounts = mock(AccountRepository.class);
        var patients = mock(PatientAccountIdentityPort.class);
        UUID accountId = UUID.randomUUID(), patientId = UUID.randomUUID();
        var earlierB = new AccountEmailChangeStore.PatientEmailSync(UUID.randomUUID(), accountId,
                patientId, "b@example.test");
        var laterC = new AccountEmailChangeStore.PatientEmailSync(UUID.randomUUID(), accountId,
                patientId, "c@example.test");
        when(syncs.claimPatientProfileSyncs(10)).thenReturn(List.of(earlierB, laterC));
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(Account.create(UUID.randomUUID(),
                "c@example.test", "hash", null, patientId, Role.PATIENT)));
        new PatientAccountEmailSyncDispatcher(syncs, accounts, patients).dispatch();

        verify(patients, times(2)).updateEmail(patientId, "c@example.test");
        verify(patients, never()).updateEmail(patientId, "b@example.test");
        verify(patients, never()).email(patientId);
        verify(syncs).completePatientProfileSync(earlierB.syncId());
        verify(syncs).completePatientProfileSync(laterC.syncId());
        verify(syncs, never()).retryPatientProfileSync(any());
    }

    @Test
    void fillsLegacyPatientProfileWithMissingEmailFromAuthoritativeAccount() {
        var syncs = mock(AccountEmailChangeStore.class);
        var accounts = mock(AccountRepository.class);
        var patients = mock(PatientAccountIdentityPort.class);
        UUID accountId = UUID.randomUUID(), patientId = UUID.randomUUID();
        var sync = new AccountEmailChangeStore.PatientEmailSync(UUID.randomUUID(), accountId,
                patientId, "new@example.test");
        when(syncs.claimPatientProfileSyncs(10)).thenReturn(List.of(sync));
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(Account.create(UUID.randomUUID(),
                "new@example.test", "hash", null, patientId, Role.PATIENT)));

        new PatientAccountEmailSyncDispatcher(syncs, accounts, patients).dispatch();

        verify(patients).updateEmail(patientId, "new@example.test");
        verify(patients, never()).email(patientId);
        verify(syncs).completePatientProfileSync(sync.syncId());
        verify(syncs, never()).retryPatientProfileSync(any());
    }
}
