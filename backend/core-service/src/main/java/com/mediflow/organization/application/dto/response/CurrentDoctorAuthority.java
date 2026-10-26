package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Current Core-owned account, staff, and home-department identity for Clinical's own actor. */
public record CurrentDoctorAuthority(
        UUID actorAccountId,
        boolean accountExists,
        boolean accountActive,
        String currentRole,
        UUID staffId,
        boolean staffActive,
        UUID departmentId,
        boolean departmentActive,
        Instant observedAt) {}
