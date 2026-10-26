package com.mediflow.clinical.application.dto.authority;

import java.time.Instant;
import java.util.UUID;

public record CurrentDoctorAuthoritySnapshot(UUID actorAccountId, Boolean accountExists, Boolean accountActive,
        String currentRole, UUID staffId, Boolean staffActive, UUID departmentId, Boolean departmentActive,
        Instant observedAt) {}
