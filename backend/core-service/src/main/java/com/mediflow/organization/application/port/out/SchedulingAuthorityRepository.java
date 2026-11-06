package com.mediflow.organization.application.port.out;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.domain.model.Role;

import java.util.Optional;
import java.util.UUID;

/** Organization-store operations for the outpatient schedule-management authority. */
public interface SchedulingAuthorityRepository {
    Optional<AccountAuthority> findAccount(UUID accountId);

    Optional<AccountAuthority> lockAccount(UUID accountId);

    Optional<DepartmentAuthority> findDepartment(UUID departmentId);

    SchedulingAuthorityData findSchedulingAuthority(UUID actorAccountId, UUID departmentId);

    CurrentDoctorAuthorityData findCurrentDoctorAuthority(UUID actorAccountId);

    ConsultationEligibilityData findConsultationEligibility(UUID doctorId, UUID departmentId, UUID roomId);

    PageResult<RoomSummary> findConsultationRooms(UUID departmentId, int page, int size);

    record AccountAuthority(UUID accountId, boolean active, Role role, UUID staffId) {}

    record DepartmentAuthority(UUID departmentId, boolean active) {}

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
