package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SurgicalCapabilityDTO(UUID staffId, String teamRole, UUID departmentId,
        boolean active, Instant validFrom, Instant validUntil, long revision, Instant updatedAt) {}
