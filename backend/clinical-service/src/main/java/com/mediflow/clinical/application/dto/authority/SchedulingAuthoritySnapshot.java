package com.mediflow.clinical.application.dto.authority;

import java.time.Instant;
import java.util.UUID;

public record SchedulingAuthoritySnapshot(
        UUID actorAccountId,
        Boolean accountExists,
        Boolean accountActive,
        String currentRole,
        UUID staffId,
        Boolean staffActive,
        UUID departmentId,
        Boolean departmentActive,
        Boolean canManageSchedules,
        Long scopeRevision,
        Instant observedAt) {
}
