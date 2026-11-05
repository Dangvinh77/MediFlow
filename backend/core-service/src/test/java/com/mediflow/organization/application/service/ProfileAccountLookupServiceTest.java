package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.model.Staff;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileAccountLookupServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final PatientAccountIdentityPort patients = mock(PatientAccountIdentityPort.class);
    private final LookupProfileAccountService service =
            new LookupProfileAccountService(accounts, staff, patients);

    @Test
    void staffLookupUsesOwnerIdAndReturnsAccountAndAuthoritativeEmailState() {
        UUID staffId = UUID.randomUUID();
        Staff owner = staff(staffId, "Alex@example.test");
        Account linked = Account.create(UUID.randomUUID(), "alex@example.test", "hash", staffId,
                null, Role.NURSE);
        when(staff.findById(staffId)).thenReturn(Optional.of(owner));
        when(accounts.findByOwner(staffId, null)).thenReturn(List.of(linked));

        var result = service.staff(staffId);

        assertThat(result.account()).isSameAs(linked);
        assertThat(result.profileEmail()).isEqualTo("Alex@example.test");
        assertThat(result.emailSyncPending()).isFalse();
        verify(accounts).findByOwner(staffId, null);
        verify(accounts, never()).findByUsername(owner.getEmail());
    }

    @Test
    void patientLookupResolvesPatientByIdAndReportsPendingProfileEmailSync() {
        UUID patientId = UUID.randomUUID();
        Account linked = Account.create(UUID.randomUUID(), "old@example.test", "hash", null,
                patientId, Role.PATIENT);
        when(patients.profileEmail(patientId)).thenReturn("new@example.test");
        when(accounts.findByOwner(null, patientId)).thenReturn(List.of(linked));

        var result = service.patient(patientId);

        assertThat(result.account()).isSameAs(linked);
        assertThat(result.profileEmail()).isEqualTo("new@example.test");
        assertThat(result.emailSyncPending()).isTrue();
        verify(accounts).findByOwner(null, patientId);
        verify(accounts, never()).findByUsername("new@example.test");
    }

    @Test
    void missingStaffOwnerIsNotReportedAsAnUnlinkedAccount() {
        UUID staffId = UUID.randomUUID();
        when(staff.findById(staffId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.staff(staffId))
                .isInstanceOf(StaffNotFoundException.class);
        verify(accounts, never()).findByOwner(staffId, null);
    }

    private static Staff staff(UUID id, String email) {
        Staff created = Staff.create("Alex", UUID.randomUUID(), JobTitle.NURSE,
                null, null, null, email, "012345678901");
        return Staff.reconstitute(id, created.getFullName(), created.getDepartmentId(), created.getJobTitle(),
                created.getSpecialization(), created.getLicenseNumber(), created.getPhoneNumber(),
                created.getEmail(), true, created.getCreatedAt(), created.getUpdatedAt(), created.getIdentityNumber());
    }
}
