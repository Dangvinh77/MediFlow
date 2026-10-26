package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScheduleManagementScopeSnapshot(
        UUID accountId,
        long revision,
        List<UUID> departmentIds,
        Instant updatedAt,
        UUID updatedByAccountId) {

    public ScheduleManagementScopeSnapshot {
        departmentIds = List.copyOf(departmentIds);
    }
}
