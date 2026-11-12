package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A staff member's stated purpose for reading one patient's records until it expires. */
public record RecordAccessDeclaration(
        UUID declarationId,
        UUID actorAccountId,
        UUID actorStaffId,
        String actorRole,
        UUID patientId,
        String purpose,
        String reason,
        Instant declaredAt,
        Instant expiresAt) {
}
