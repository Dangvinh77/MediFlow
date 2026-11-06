package com.mediflow.organization.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.exception.SchedulingScopeDeniedException;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.AccountAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.DepartmentAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.SchedulingAuthorityData;
import com.mediflow.organization.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ScheduleManagementScopeServiceTest {
    private final UUID actor = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();

    @Mock SchedulingAuthorityRepository repository;
    private ScheduleManagementScopeService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleManagementScopeService(repository, Clock.systemUTC());
    }

    @Test
    void departmentHeadListsRoomsOfTheirDepartment() {
        when(repository.findAccount(actor)).thenReturn(Optional.of(
                new AccountAuthority(actor, true, Role.DOCTOR, UUID.randomUUID())));
        when(repository.findSchedulingAuthority(actor, department)).thenReturn(authority(true));
        when(repository.findDepartment(department)).thenReturn(Optional.of(new DepartmentAuthority(department, true)));
        when(repository.findConsultationRooms(department, 0, 20)).thenReturn(PageResult.of(List.of(), 0, 0, 20));

        service.listConsultationRooms(actor, Role.DOCTOR, department, 0, 20);

        verify(repository).findConsultationRooms(department, 0, 20);
    }

    @Test
    void doctorWithoutAppointmentCannotListRooms() {
        when(repository.findAccount(actor)).thenReturn(Optional.of(
                new AccountAuthority(actor, true, Role.DOCTOR, UUID.randomUUID())));
        when(repository.findSchedulingAuthority(actor, department)).thenReturn(authority(false));

        assertThatThrownBy(() -> service.listConsultationRooms(actor, Role.DOCTOR, department, 0, 20))
                .isInstanceOf(SchedulingScopeDeniedException.class);
        verify(repository, never()).findConsultationRooms(department, 0, 20);
    }

    @Test
    void otherRolesCannotListRooms() {
        when(repository.findAccount(actor)).thenReturn(Optional.of(
                new AccountAuthority(actor, true, Role.NURSE, UUID.randomUUID())));

        assertThatThrownBy(() -> service.listConsultationRooms(actor, Role.NURSE, department, 0, 20))
                .isInstanceOf(SchedulingScopeDeniedException.class);
    }

    private SchedulingAuthorityData authority(boolean canManage) {
        return new SchedulingAuthorityData(true, true, "DOCTOR", UUID.randomUUID(), true, true, canManage, 1);
    }
}
