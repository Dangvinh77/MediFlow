package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OperatingRoomLookupDTO(boolean exists, boolean active, UUID roomId,
        UUID departmentId, String sourceRevision, Instant observedAt) {}
