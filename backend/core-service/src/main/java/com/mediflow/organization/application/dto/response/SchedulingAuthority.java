package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SchedulingAuthority(
        UUID actorAccountId,
        boolean accountExists,
        boolean accountActive,
        String currentRole,
        UUID staffId,
        boolean staffActive,
        UUID departmentId,
        boolean departmentActive,
        boolean canManageSchedules,
        long scopeRevision,
        Instant observedAt) {}
