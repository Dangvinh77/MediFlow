package com.mediflow.organization.application.port.out;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Organization-store operations for the outpatient schedule-management authority. */
public interface SchedulingAuthorityRepository {
    Optional<AccountAuthority> findAccount(UUID accountId);

    Optional<AccountAuthority> lockAccount(UUID accountId);

    Optional<StaffAuthority> findStaff(UUID staffId);

    Optional<StaffAuthority> lockStaff(UUID staffId);

    Optional<DepartmentAuthority> findDepartment(UUID departmentId);

    Optional<DepartmentAuthority> lockDepartment(UUID departmentId);

    ScopeState findScope(UUID accountId);

    Optional<ScopeCommandReceipt> findReceipt(UUID actorAccountId, String method,
            String canonicalPath, String idempotencyKey);

    void saveScope(UUID accountId, long revision, List<UUID> departmentIds,
            Instant updatedAt, UUID updatedByAccountId);

    void appendAudit(ScopeAudit audit);

    void saveReceipt(ScopeCommandReceipt receipt);

    SchedulingAuthorityData findSchedulingAuthority(UUID actorAccountId, UUID departmentId);

    CurrentDoctorAuthorityData findCurrentDoctorAuthority(UUID actorAccountId);

    ConsultationEligibilityData findConsultationEligibility(UUID doctorId, UUID departmentId, UUID roomId);

    PageResult<RoomSummary> findConsultationRooms(UUID departmentId, int page, int size);

    record AccountAuthority(UUID accountId, boolean active, Role role, UUID staffId) {}

    record StaffAuthority(UUID staffId, boolean active, JobTitle jobTitle, UUID departmentId) {}

    record DepartmentAuthority(UUID departmentId, boolean active) {}

    record ScopeState(long revision, List<UUID> departmentIds, Instant updatedAt, UUID updatedByAccountId) {
        public ScopeState {
            departmentIds = List.copyOf(departmentIds);
        }
    }

    record ScopeCommandReceipt(UUID actorAccountId, UUID accountId, String method, String canonicalPath,
            String idempotencyKey, long expectedRevision, List<UUID> departmentIds, String reason,
            ScheduleManagementScopeSnapshot response, int responseStatus, String responseLocation) {
        public ScopeCommandReceipt {
            departmentIds = List.copyOf(departmentIds);
        }
    }

    record ScopeAudit(UUID auditId, UUID accountId, long revision, Long previousRevision,
            UUID actorAccountId, String reason, List<UUID> previousDepartmentIds,
            List<UUID> departmentIds, Instant occurredAt) {
        public ScopeAudit {
            previousDepartmentIds = List.copyOf(previousDepartmentIds);
            departmentIds = List.copyOf(departmentIds);
        }
    }

    record SchedulingAuthorityData(boolean accountExists, boolean accountActive, String currentRole,
            UUID staffId, boolean staffActive, boolean departmentActive, boolean canManageSchedules,
            long scopeRevision) {}

    record CurrentDoctorAuthorityData(boolean accountExists, boolean accountActive, String currentRole,
            UUID staffId, boolean staffActive, UUID departmentId, boolean departmentActive) {}

    record ConsultationEligibilityData(boolean doctorExists, boolean doctorActive,
            boolean eligibleDoctor, UUID doctorDepartmentId, boolean departmentExists,
            boolean departmentActive, boolean roomExists, boolean roomActive,
            UUID roomDepartmentId, String roomType, boolean eligible) {}
}
