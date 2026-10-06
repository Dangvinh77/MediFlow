package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgicalEligibilityResponse(Boolean exists, Boolean eligible, UUID staffId,
        String teamRole, UUID departmentId, String sourceRevision, Instant observedAt,
        Instant startsAt, Instant endsAt) {}
