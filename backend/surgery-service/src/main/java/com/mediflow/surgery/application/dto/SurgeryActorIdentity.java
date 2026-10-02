package com.mediflow.surgery.application.dto;

import java.util.UUID;

/** Verified human identity translated from the authenticated request by the driving adapter. */
public record SurgeryActorIdentity(UUID accountId, UUID verifiedStaffId) {
    public SurgeryActorIdentity {
        if (accountId == null) throw new IllegalArgumentException("Verified account id is required");
    }
}
