package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LabReleaseAuthority(
        UUID actorAccountId,
        boolean accountActive,
        UUID staffId,
        boolean staffActive,
        boolean canReleaseLabResults,
        Instant observedAt) {}
