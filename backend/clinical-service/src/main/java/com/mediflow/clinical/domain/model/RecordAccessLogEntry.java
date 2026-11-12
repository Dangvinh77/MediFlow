package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One read decision on a medical record. Holds identifiers only, never clinical content. */
public record RecordAccessLogEntry(
        UUID actorAccountId,
        UUID actorStaffId,
        String actorRole,
        UUID patientId,
        UUID recordId,
        String action,
        String outcome,
        String basis,
        String purpose,
        String correlationId,
        Instant occurredAt) {
}
