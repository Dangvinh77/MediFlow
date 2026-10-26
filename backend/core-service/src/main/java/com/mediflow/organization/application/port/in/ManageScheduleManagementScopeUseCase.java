package com.mediflow.organization.application.port.in;

import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.request.ScheduleManagementScopeCommand;
import com.mediflow.organization.application.dto.response.ConsultationEligibility;
import com.mediflow.organization.application.dto.response.CurrentDoctorAuthority;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeCommandResult;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.application.dto.response.SchedulingAuthority;
import com.mediflow.organization.domain.model.Role;

import java.util.UUID;

public interface ManageScheduleManagementScopeUseCase {
    ScheduleManagementScopeSnapshot getScope(UUID actorAccountId, Role signedRole, UUID targetAccountId);

    ScheduleManagementScopeCommandResult replaceScope(UUID actorAccountId, Role signedRole,
            UUID targetAccountId, ScheduleManagementScopeCommand command,
            String idempotencyKey, String correlationId);

    SchedulingAuthority getSchedulingAuthority(UUID actorAccountId, UUID departmentId);

    ConsultationEligibility getConsultationEligibility(UUID doctorId, UUID departmentId, UUID roomId);

    CurrentDoctorAuthority getCurrentDoctorAuthority(UUID actorAccountId);

    PageResult<RoomSummary> listConsultationRooms(UUID actorAccountId, Role signedRole,
            UUID departmentId, int page, int size);
}
