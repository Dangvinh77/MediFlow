package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OperatingRoomLookupResponse(Boolean exists, Boolean active, UUID roomId,
        UUID departmentId, String sourceRevision, Instant observedAt) {}
