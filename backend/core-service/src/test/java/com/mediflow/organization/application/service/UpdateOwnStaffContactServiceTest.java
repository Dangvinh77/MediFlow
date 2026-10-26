package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateOwnStaffContactServiceTest {
    private final StaffRepository staff = mock(StaffRepository.class);
    private final com.mediflow.organization.application.port.out.EventPublisher events = mock(com.mediflow.organization.application.port.out.EventPublisher.class);
    private final UpdateOwnStaffContactService service = new UpdateOwnStaffContactService(staff, events, UUID::randomUUID);

    @Test
    void updatesOnlyOwnContactAndPreservesIdentityEmploymentAndProfile() {
        var member = Staff.create("A", UUID.randomUUID(), JobTitle.DOCTOR, "Internal", "LIC-1", null, null, "001234567890");
        member.withProfile(new StaffProfile(LocalDate.of(1990, 1, 1), StaffProfile.Gender.FEMALE,
                "Old", LocalDate.of(2020, 1, 1), "Specialist"));
        when(staff.findByIdForUpdate(member.getStaffId())).thenReturn(Optional.of(member));
        when(staff.save(any())).thenAnswer(call -> call.getArgument(0));
        var before = member.getProfile();
        var department = member.getDepartmentId();
        var result = service.update(member.getStaffId(), member.getStaffId(), " 0901234567 ", " a@example.test ", " New ");
        verify(events).publishStaffContactUpdated(any());
        assertThat(result.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(result.getEmail()).isEqualTo("a@example.test");
        assertThat(result.getProfile()).isEqualTo(new StaffProfile(before.dateOfBirth(), before.gender(), "New", before.hireDate(), before.qualification()));
        assertThat(result.getDepartmentId()).isEqualTo(department);
        assertThat(result.getJobTitle()).isEqualTo(JobTitle.DOCTOR);
        assertThat(result.getLicenseNumber()).isEqualTo("LIC-1");
        assertThat(result.getIdentityNumber()).isEqualTo("001234567890");
    }

    @Test
    void rejectsForeignAndMissingSignedIdentityBeforeRepositoryAccess() {
        UUID own = UUID.randomUUID();
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), own, null, null, null))
                .isInstanceOf(com.mediflow.organization.domain.exception.OwnStaffContactAccessDeniedException.class);
        assertThatThrownBy(() -> service.update(own, null, null, null, null))
                .isInstanceOf(com.mediflow.organization.domain.exception.OwnStaffContactAccessDeniedException.class);
        verifyNoInteractions(staff, events);
    }

    @Test
    void invalidContactDoesNotSave() {
        var member = Staff.create("A", UUID.randomUUID(), JobTitle.NURSE, null, null, null, null, "001234567890");
        when(staff.findByIdForUpdate(member.getStaffId())).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.update(member.getStaffId(), member.getStaffId(), "abc", null, null))
                .isInstanceOf(com.mediflow.organization.domain.exception.InvalidStaffDataException.class);
        verify(staff, never()).save(any());
        verifyNoInteractions(events);
    }
}
