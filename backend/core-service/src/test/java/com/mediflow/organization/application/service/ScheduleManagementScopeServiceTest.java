package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.dto.request.ScheduleManagementScopeCommand;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeCommandResult;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.application.event.ScheduleManagementScopeChangedEvent;
import com.mediflow.organization.application.port.out.ScheduleManagementScopeEventPublisher;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.AccountAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.DepartmentAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.ScopeState;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.StaffAuthority;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleManagementScopeServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");
    private static final String PATH = "/api/v2/org/accounts/%s/schedule-management-scope";

    @Mock SchedulingAuthorityRepository repository;
    @Mock ScheduleManagementScopeEventPublisher events;

    private ScheduleManagementScopeService service;
    private UUID adminId;
    private UUID managerId;
    private UUID managerStaffId;
    private UUID homeDepartmentId;
    private UUID managedDepartmentId;

    @BeforeEach
    void setUp() {
        service = new ScheduleManagementScopeService(repository, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        adminId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        managerStaffId = UUID.randomUUID();
        homeDepartmentId = UUID.randomUUID();
        managedDepartmentId = UUID.randomUUID();
    }

    @Test
    void adminGrant_replacesWholeScopeAndPersistsRevisionAuditReceiptAndEvent() {
        AccountAuthority admin = new AccountAuthority(adminId, true, Role.ADMIN, null);
        AccountAuthority manager = new AccountAuthority(managerId, true, Role.MANAGER, managerStaffId);
        when(repository.lockAccount(adminId)).thenReturn(Optional.of(admin));
        when(repository.lockAccount(managerId)).thenReturn(Optional.of(manager));
        when(repository.findReceipt(adminId, "PUT", PATH.formatted(managerId), "scope-key"))
                .thenReturn(Optional.empty());
        when(repository.findScope(managerId)).thenReturn(new ScopeState(0, List.of(), null, null));
        when(repository.lockStaff(managerStaffId)).thenReturn(Optional.of(
                new StaffAuthority(managerStaffId, true, JobTitle.MANAGER, homeDepartmentId)));
        when(repository.lockDepartment(homeDepartmentId)).thenReturn(Optional.of(
                new DepartmentAuthority(homeDepartmentId, true)));
        when(repository.lockDepartment(managedDepartmentId)).thenReturn(Optional.of(
                new DepartmentAuthority(managedDepartmentId, true)));

        ScheduleManagementScopeCommandResult result = service.replaceScope(adminId, Role.ADMIN,
                managerId, new ScheduleManagementScopeCommand(0L,
                        List.of(managedDepartmentId), "Approved manager scope"),
                "scope-key", "trace-c01-001");

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.snapshot()).isEqualTo(new ScheduleManagementScopeSnapshot(
                managerId, 1, List.of(managedDepartmentId), NOW, adminId));
        verify(repository).saveScope(managerId, 1, List.of(managedDepartmentId), NOW, adminId);
        verify(repository).appendAudit(any());
        verify(repository).saveReceipt(any());
        verify(events).publish(any(ScheduleManagementScopeChangedEvent.class));
    }

    @Test
    void exactReplayReturnsOriginalSnapshotWithoutCreatingAnotherRevisionOrEvent() {
        AccountAuthority admin = new AccountAuthority(adminId, true, Role.ADMIN, null);
        AccountAuthority manager = new AccountAuthority(managerId, true, Role.MANAGER, managerStaffId);
        ScheduleManagementScopeSnapshot original = new ScheduleManagementScopeSnapshot(
                managerId, 1, List.of(managedDepartmentId), NOW, adminId);
        var receipt = new SchedulingAuthorityRepository.ScopeCommandReceipt(
                adminId, managerId, "PUT", PATH.formatted(managerId), "scope-key", 0,
                List.of(managedDepartmentId), "Approved manager scope", original, 200, null);
        when(repository.lockAccount(adminId)).thenReturn(Optional.of(admin));
        when(repository.lockAccount(managerId)).thenReturn(Optional.of(manager));
        when(repository.findReceipt(adminId, "PUT", PATH.formatted(managerId), "scope-key"))
                .thenReturn(Optional.of(receipt));

        ScheduleManagementScopeCommandResult result = service.replaceScope(adminId, Role.ADMIN,
                managerId, new ScheduleManagementScopeCommand(0L,
                        List.of(managedDepartmentId), "Approved manager scope"),
                "scope-key", "trace-c01-replay");

        assertThat(result.replayed()).isTrue();
        assertThat(result.snapshot()).isEqualTo(original);
        verify(repository, never()).saveScope(any(), any(Long.class), any(), any(), any());
        verify(repository, never()).appendAudit(any());
        verify(repository, never()).saveReceipt(any());
        verify(events, never()).publish(any());
    }

    @Test
    void reusingKeyWithDifferentTypedInputConflicts() {
        AccountAuthority admin = new AccountAuthority(adminId, true, Role.ADMIN, null);
        AccountAuthority manager = new AccountAuthority(managerId, true, Role.MANAGER, managerStaffId);
        ScheduleManagementScopeSnapshot original = new ScheduleManagementScopeSnapshot(
                managerId, 1, List.of(managedDepartmentId), NOW, adminId);
        var receipt = new SchedulingAuthorityRepository.ScopeCommandReceipt(
                adminId, managerId, "PUT", PATH.formatted(managerId), "scope-key", 0,
                List.of(managedDepartmentId), "First reason", original, 200, null);
        when(repository.lockAccount(adminId)).thenReturn(Optional.of(admin));
        when(repository.lockAccount(managerId)).thenReturn(Optional.of(manager));
        when(repository.findReceipt(adminId, "PUT", PATH.formatted(managerId), "scope-key"))
                .thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.replaceScope(adminId, Role.ADMIN, managerId,
                new ScheduleManagementScopeCommand(0L,
                        List.of(managedDepartmentId), "Different reason"),
                "scope-key", "trace-c01-conflict"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_CONFLICT");
        verify(repository, never()).saveScope(any(), any(Long.class), any(), any(), any());
        verify(events, never()).publish(any());
    }

    @Test
    void inactiveManagerCannotReceiveNonemptyScopeButCanBeRevoked() {
        AccountAuthority admin = new AccountAuthority(adminId, true, Role.ADMIN, null);
        AccountAuthority manager = new AccountAuthority(managerId, false, Role.MANAGER, managerStaffId);
        when(repository.lockAccount(adminId)).thenReturn(Optional.of(admin));
        when(repository.lockAccount(managerId)).thenReturn(Optional.of(manager));
        when(repository.findReceipt(eq(adminId), eq("PUT"), eq(PATH.formatted(managerId)), any()))
                .thenReturn(Optional.empty());
        when(repository.findScope(managerId)).thenReturn(new ScopeState(0, List.of(), null, null));

        assertThatThrownBy(() -> service.replaceScope(adminId, Role.ADMIN, managerId,
                new ScheduleManagementScopeCommand(0L,
                        List.of(managedDepartmentId), "Grant after deactivation"),
                "grant-key", "trace-c01-denied"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_INELIGIBLE");

        ScheduleManagementScopeCommandResult revoked = service.replaceScope(adminId, Role.ADMIN,
                managerId, new ScheduleManagementScopeCommand(0L, List.of(), "Revoke inactive scope"),
                "revoke-key", "trace-c01-revoke");
        assertThat(revoked.snapshot().revision()).isEqualTo(1);
        assertThat(revoked.snapshot().departmentIds()).isEmpty();
    }
}
