package com.mediflow.organization.application.service;

import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.dto.request.ScheduleManagementScopeCommand;
import com.mediflow.organization.application.dto.response.ConsultationEligibility;
import com.mediflow.organization.application.dto.response.CurrentDoctorAuthority;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeCommandResult;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.application.dto.response.SchedulingAuthority;
import com.mediflow.organization.application.event.ScheduleManagementScopeChangedEvent;
import com.mediflow.organization.application.exception.SchedulingAuthorityUnavailableException;
import com.mediflow.organization.application.exception.SchedulingScopeDeniedException;
import com.mediflow.organization.application.port.in.ManageScheduleManagementScopeUseCase;
import com.mediflow.organization.application.port.out.ScheduleManagementScopeEventPublisher;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.AccountAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.ConsultationEligibilityData;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.DepartmentAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.SchedulingAuthorityData;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.ScopeCommandReceipt;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.ScopeState;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.StaffAuthority;
import com.mediflow.organization.domain.model.Role;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Application rules for Core-owned schedule-management authority and consultation resources. */
public class ScheduleManagementScopeService implements ManageScheduleManagementScopeUseCase {
    private static final String METHOD = "PUT";
    private static final String PRODUCER = "core-service";
    private static final String ROOM_TYPE_CONSULTATION = "CONSULTATION";

    private final SchedulingAuthorityRepository repository;
    private final ScheduleManagementScopeEventPublisher events;
    private final Clock clock;

    public ScheduleManagementScopeService(SchedulingAuthorityRepository repository,
            ScheduleManagementScopeEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public ScheduleManagementScopeSnapshot getScope(UUID actorAccountId, Role signedRole,
            UUID targetAccountId) {
        currentHumanActor(actorAccountId, signedRole, Role.ADMIN);
        if (repository.findAccount(targetAccountId).isEmpty()) {
            throw resourceNotFound();
        }
        return snapshot(targetAccountId, repository.findScope(targetAccountId));
    }

    @Override
    @Transactional(transactionManager = "organizationTransactionManager")
    public ScheduleManagementScopeCommandResult replaceScope(UUID actorAccountId, Role signedRole,
            UUID targetAccountId, ScheduleManagementScopeCommand command,
            String idempotencyKey, String correlationId) {
        validateCommand(command, idempotencyKey, correlationId);
        List<UUID> departmentIds = command.departmentIds().stream().distinct().sorted().toList();
        if (departmentIds.size() != command.departmentIds().size()) {
            throw new IllegalArgumentException("departmentIds must contain unique UUIDs");
        }
        if (signedRole != Role.ADMIN) throw new SchedulingScopeDeniedException();
        if (actorAccountId == null) throw new SchedulingScopeDeniedException();
        if (targetAccountId == null) throw new IllegalArgumentException("target account is required");

        Map<UUID, AccountAuthority> lockedAccounts = new LinkedHashMap<>();
        List.of(actorAccountId, targetAccountId).stream().distinct().sorted().forEach(id ->
                repository.lockAccount(id).ifPresent(account -> lockedAccounts.put(id, account)));
        AccountAuthority actor = lockedAccounts.get(actorAccountId);
        requireCurrentAdmin(actor, signedRole);
        AccountAuthority target = lockedAccounts.get(targetAccountId);
        if (target == null) throw resourceNotFound();

        String path = scopePath(targetAccountId);
        Optional<ScopeCommandReceipt> previousReceipt = repository.findReceipt(
                actorAccountId, METHOD, path, idempotencyKey);
        if (previousReceipt.isPresent()) {
            ScopeCommandReceipt receipt = previousReceipt.get();
            if (receipt.expectedRevision() != command.expectedRevision()
                    || !receipt.departmentIds().equals(departmentIds)
                    || !receipt.reason().equals(command.reason())) {
                throw idempotencyConflict();
            }
            return new ScheduleManagementScopeCommandResult(receipt.response(), true,
                    receipt.responseStatus(), receipt.responseLocation());
        }

        ScopeState previous = repository.findScope(targetAccountId);
        if (previous.revision() != command.expectedRevision()) {
            throw new DuplicateResourceException("REVISION_CONFLICT",
                    "Schedule-management scope revision is stale");
        }
        if (!departmentIds.isEmpty()) validateGrantTarget(target);
        for (UUID departmentId : departmentIds) requireActiveDepartment(departmentId);

        long nextRevision;
        try {
            nextRevision = Math.addExact(previous.revision(), 1);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Scope revision cannot be incremented");
        }
        // PostgreSQL timestamptz persists microsecond precision; normalize before returning
        // and storing so the first response and a durable idempotent replay are identical.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ScheduleManagementScopeSnapshot updated = new ScheduleManagementScopeSnapshot(
                targetAccountId, nextRevision, departmentIds, now, actorAccountId);
        repository.saveScope(targetAccountId, nextRevision, departmentIds, now, actorAccountId);
        repository.appendAudit(new SchedulingAuthorityRepository.ScopeAudit(
                UUID.randomUUID(), targetAccountId, nextRevision, previous.revision(), actorAccountId,
                command.reason(), previous.departmentIds(), departmentIds, now));
        events.publish(new ScheduleManagementScopeChangedEvent(UUID.randomUUID(),
                ScheduleManagementScopeChangedEvent.ROUTING_KEY, 1, now, correlationId, PRODUCER,
                new ScheduleManagementScopeChangedEvent.Payload(targetAccountId, nextRevision,
                        departmentIds, actorAccountId, command.reason())));
        repository.saveReceipt(new ScopeCommandReceipt(actorAccountId, targetAccountId, METHOD, path,
                idempotencyKey, command.expectedRevision(), departmentIds, command.reason(),
                updated, 200, null));
        return new ScheduleManagementScopeCommandResult(updated, false, 200, null);
    }

    @Override
    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public SchedulingAuthority getSchedulingAuthority(UUID actorAccountId, UUID departmentId) {
        return withUnavailable(() -> {
            SchedulingAuthorityData current = repository.findSchedulingAuthority(actorAccountId, departmentId);
            return new SchedulingAuthority(actorAccountId, current.accountExists(), current.accountActive(),
                    current.currentRole(), current.staffId(), current.staffActive(), departmentId,
                    current.departmentActive(), current.canManageSchedules(), current.scopeRevision(),
                    clock.instant());
        });
    }

    @Override
    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public ConsultationEligibility getConsultationEligibility(UUID doctorId, UUID departmentId, UUID roomId) {
        return withUnavailable(() -> {
            ConsultationEligibilityData current = repository.findConsultationEligibility(
                    doctorId, departmentId, roomId);
            return new ConsultationEligibility(doctorId, current.doctorExists(), current.doctorActive(),
                    current.eligibleDoctor(), current.doctorDepartmentId(), departmentId,
                    current.departmentExists(), current.departmentActive(), roomId, current.roomExists(),
                    current.roomActive(), current.roomDepartmentId(), current.roomType(),
                    current.eligible(), clock.instant());
        });
    }

    @Override
    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public CurrentDoctorAuthority getCurrentDoctorAuthority(UUID actorAccountId) {
        return withUnavailable(() -> {
            SchedulingAuthorityRepository.CurrentDoctorAuthorityData current =
                    repository.findCurrentDoctorAuthority(actorAccountId);
            return new CurrentDoctorAuthority(actorAccountId, current.accountExists(), current.accountActive(),
                    current.currentRole(), current.staffId(), current.staffActive(), current.departmentId(),
                    current.departmentActive(), clock.instant());
        });
    }

    @Override
    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public PageResult<RoomSummary> listConsultationRooms(UUID actorAccountId, Role signedRole,
            UUID departmentId, int page, int size) {
        if (departmentId == null || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("A department and page size from 1 to 100 are required");
        }
        return withUnavailable(() -> {
            AccountAuthority actor = currentHumanActor(actorAccountId, signedRole, Role.ADMIN, Role.MANAGER);
            if (actor.role() == Role.MANAGER) {
                requireActiveManagerStaff(actor);
                if (!repository.findScope(actorAccountId).departmentIds().contains(departmentId)) {
                    throw new SchedulingScopeDeniedException();
                }
            }
            DepartmentAuthority department = repository.findDepartment(departmentId)
                    .orElseThrow(ScheduleManagementScopeService::resourceNotFound);
            if (!department.active()) {
                throw ineligible("Consultation rooms require an active department");
            }
            return repository.findConsultationRooms(departmentId, page, size);
        });
    }

    private AccountAuthority currentHumanActor(UUID actorAccountId, Role signedRole, Role... allowedRoles) {
        AccountAuthority actor = actorAccountId == null ? null : repository.findAccount(actorAccountId).orElse(null);
        requireCurrentAdminOrManager(actor, signedRole, allowedRoles);
        return actor;
    }

    private static void requireCurrentAdminOrManager(AccountAuthority actor, Role signedRole,
            Role... allowedRoles) {
        if (actor == null || !actor.active() || signedRole == null || actor.role() != signedRole
                || java.util.Arrays.stream(allowedRoles).noneMatch(role -> role == actor.role())) {
            throw new SchedulingScopeDeniedException();
        }
    }

    private static void requireCurrentAdmin(AccountAuthority actor, Role signedRole) {
        if (actor == null || !actor.active() || actor.role() != Role.ADMIN || signedRole != Role.ADMIN) {
            throw new SchedulingScopeDeniedException();
        }
    }

    private void requireActiveManagerStaff(AccountAuthority account) {
        if (account.staffId() == null || repository.findStaff(account.staffId())
                .filter(StaffAuthority::active).isEmpty()) {
            throw new SchedulingScopeDeniedException();
        }
    }

    private void validateGrantTarget(AccountAuthority target) {
        if (!target.active() || target.role() != Role.MANAGER || target.staffId() == null) {
            throw ineligible("A nonempty scope requires an active MANAGER account linked to active staff");
        }
        StaffAuthority staff = repository.lockStaff(target.staffId())
                .filter(StaffAuthority::active)
                .orElseThrow(() -> ineligible("A nonempty scope requires active manager staff"));
        repository.lockDepartment(staff.departmentId())
                .filter(DepartmentAuthority::active)
                .orElseThrow(() -> ineligible("Manager staff must belong to an active department"));
    }

    private void requireActiveDepartment(UUID departmentId) {
        repository.lockDepartment(departmentId)
                .filter(DepartmentAuthority::active)
                .orElseThrow(() -> ineligible("Every scoped department must exist and be active"));
    }

    private ScheduleManagementScopeSnapshot snapshot(UUID accountId, ScopeState scope) {
        return new ScheduleManagementScopeSnapshot(accountId, scope.revision(), scope.departmentIds(),
                scope.updatedAt(), scope.updatedByAccountId());
    }

    private void validateCommand(ScheduleManagementScopeCommand command,
            String idempotencyKey, String correlationId) {
        if (command == null || command.expectedRevision() == null || command.expectedRevision() < 0
                || command.departmentIds() == null || command.departmentIds().size() > 100
                || command.reason() == null || command.reason().isBlank()
                || command.reason().length() > 500) {
            throw new IllegalArgumentException("Invalid schedule-management scope command");
        }
        if (idempotencyKey == null || idempotencyKey.length() > 120
                || !idempotencyKey.matches("[!-~]{1,120}")) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 120) {
            throw new IllegalArgumentException("Invalid X-Correlation-Id");
        }
        if (command.departmentIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("departmentIds cannot contain null");
        }
    }

    private <T> T withUnavailable(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SchedulingScopeDeniedException | BusinessRuleException | ResourceNotFoundException
                | IllegalArgumentException failure) {
            throw failure;
        } catch (DataAccessException failure) {
            throw new SchedulingAuthorityUnavailableException(failure);
        }
    }

    private static BusinessRuleException ineligible(String message) {
        return new BusinessRuleException("SCHEDULE_INELIGIBLE", message);
    }

    private static ResourceNotFoundException resourceNotFound() {
        return new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Resource not found");
    }

    private static DuplicateResourceException idempotencyConflict() {
        return new DuplicateResourceException("IDEMPOTENCY_CONFLICT",
                "Idempotency key was already used for a different scope command");
    }

    private static String scopePath(UUID accountId) {
        return "/api/v2/org/accounts/" + accountId + "/schedule-management-scope";
    }
}
