package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StaffCapabilityGrantDTO(
        UUID grantId,
        UUID staffId,
        String capability,
        UUID grantedBy,
        String grantReason,
        Instant grantedAt,
        UUID revokedBy,
        String revocationReason,
        Instant revokedAt) {}
