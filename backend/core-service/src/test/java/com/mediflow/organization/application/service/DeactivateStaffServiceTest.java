package com.mediflow.organization.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.event.StaffDeactivatedEvent;
import com.mediflow.organization.application.port.out.*;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeactivateStaffServiceTest {
    @Mock StaffRepository staff;
    @Mock DepartmentRepository departments;
    @Mock AccountRepository accounts;
    @Mock EventPublisher events;
    @Mock DepartmentHeadRepository heads;
    final UUID actor = UUID.randomUUID();
    final UUID correlation = UUID.randomUUID();
    final Department department = Department.create(UUID.randomUUID(), "Clinic", "CLN", DepartmentType.CLINICAL, "A");
    final Staff member = Staff.create("Doctor", department.getDepartmentId(), JobTitle.DOCTOR,
            "Internal medicine", "LICENSE", null, "doctor@example.com", "012345678901");

    DeactivateStaffService service() {
        return new DeactivateStaffService(staff, departments, accounts, events, () -> correlation, heads);
    }

    void existing() {
        when(staff.findByIdForUpdate(member.getStaffId())).thenReturn(Optional.of(member));
    }

    @Test void stopsStaffRevokesEveryActiveAccountAndPublishesOneFact() {
        existing();
        when(departments.findByIdForUpdate(department.getDepartmentId())).thenReturn(Optional.of(department));
        var first = Account.create(UUID.randomUUID(), "first", "hash", member.getStaffId(), null, Role.DOCTOR);
        var second = Account.create(UUID.randomUUID(), "second", "hash", member.getStaffId(), null, Role.DOCTOR);
        when(accounts.findActiveByOwner(member.getStaffId(), null)).thenReturn(List.of(first, second));
        when(staff.save(member)).thenReturn(member);

        assertThat(service().execute(member.getStaffId(), actor).isActive()).isFalse();
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        verify(accounts).incrementCredentialVersion(first.getAccountId());
        verify(accounts).incrementCredentialVersion(second.getAccountId());
        var order = inOrder(heads, accounts, staff, events);
        order.verify(heads).lockCommands();
        order.verify(accounts).lockOwner(member.getStaffId(), null);
        order.verify(accounts).findActiveByOwner(member.getStaffId(), null);
        order.verify(staff).findByIdForUpdate(member.getStaffId());
        verify(events).publishStaffDeactivated(argThat(event -> event.staffId().equals(member.getStaffId())
                && event.actorAccountId().equals(actor) && event.correlationId().equals(correlation.toString())));
    }

    @Test void canStopStaffWithoutAnyAccountAndRepeatIsNoOp() {
        existing();
        when(departments.findByIdForUpdate(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(staff.save(member)).thenReturn(member);
        service().execute(member.getStaffId(), actor);
        var stoppedAt = member.getUpdatedAt();
        service().execute(member.getStaffId(), actor);
        assertThat(member.getUpdatedAt()).isEqualTo(stoppedAt);
        verify(staff, times(1)).save(member);
        verify(events, times(1)).publishStaffDeactivated(any(StaffDeactivatedEvent.class));
        verify(accounts, never()).save(any());
    }

    @Test void departmentHeadMustBeReassignedFirst() {
        existing();
        department.changeHead(member);
        when(departments.findByIdForUpdate(department.getDepartmentId())).thenReturn(Optional.of(department));
        assertThatThrownBy(() -> service().execute(member.getStaffId(), actor))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("trưởng");
        assertThat(member.isActive()).isTrue();
        verify(staff, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test void cannotStopOnesOwnProfile() {
        var own = Account.create(actor, "admin", "hash", member.getStaffId(), null, Role.ADMIN);
        when(accounts.findActiveByOwner(member.getStaffId(), null)).thenReturn(List.of(own));
        assertThatThrownBy(() -> service().execute(member.getStaffId(), actor))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("chính mình");
        verifyNoInteractions(staff, events);
        verify(accounts, never()).save(any());
    }

    @Test void unknownStaffIsNotFound() {
        assertThatThrownBy(() -> service().execute(UUID.randomUUID(), actor)).isInstanceOf(StaffNotFoundException.class);
        verifyNoInteractions(events);
    }
}
