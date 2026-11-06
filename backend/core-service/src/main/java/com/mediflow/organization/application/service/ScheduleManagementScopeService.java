package com.mediflow.organization.application.service;

import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.dto.response.ConsultationEligibility;
import com.mediflow.organization.application.dto.response.CurrentDoctorAuthority;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.dto.response.SchedulingAuthority;
import com.mediflow.organization.application.exception.SchedulingAuthorityUnavailableException;
import com.mediflow.organization.application.exception.SchedulingScopeDeniedException;
import com.mediflow.organization.application.port.in.ManageScheduleManagementScopeUseCase;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.AccountAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.ConsultationEligibilityData;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.DepartmentAuthority;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.SchedulingAuthorityData;
import com.mediflow.organization.domain.model.Role;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/** Application rules for Core-owned schedule-management authority and consultation resources. */
public class ScheduleManagementScopeService implements ManageScheduleManagementScopeUseCase {
    private static final String ROOM_TYPE_CONSULTATION = "CONSULTATION";

    private final SchedulingAuthorityRepository repository;
    private final Clock clock;

    public ScheduleManagementScopeService(SchedulingAuthorityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
            AccountAuthority actor = currentHumanActor(actorAccountId, signedRole, Role.ADMIN, Role.DOCTOR);
            if (actor.role() == Role.DOCTOR && !repository.findSchedulingAuthority(actorAccountId,departmentId).canManageSchedules()) {
                throw new SchedulingScopeDeniedException();
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
        requireCurrentActor(actor, signedRole, allowedRoles);
        return actor;
    }

    private static void requireCurrentActor(AccountAuthority actor, Role signedRole,
            Role... allowedRoles) {
        if (actor == null || !actor.active() || signedRole == null || actor.role() != signedRole
                || java.util.Arrays.stream(allowedRoles).noneMatch(role -> role == actor.role())) {
            throw new SchedulingScopeDeniedException();
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

        }
