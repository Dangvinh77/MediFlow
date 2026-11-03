package com.mediflow.organization.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface StaffIdentityAuditPort {
    void record(UUID staffId, UUID changedByAccountId, String reason, String oldIdentityNumber,
            String newIdentityNumber, Instant changedAt);
}
