package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SurgicalEligibilityDTO(boolean exists, boolean eligible, UUID staffId,
        String teamRole, UUID departmentId, String sourceRevision, Instant observedAt,
        Instant startsAt, Instant endsAt) {}
