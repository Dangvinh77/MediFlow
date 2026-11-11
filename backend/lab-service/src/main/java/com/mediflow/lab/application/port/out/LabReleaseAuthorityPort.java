package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** Organization's live answer on whether this account and staff member may release Lab results. */
public interface LabReleaseAuthorityPort {
    /** False whenever Organization denies, cannot be reached, or answers for someone else. */
    boolean canRelease(UUID actorAccountId, UUID actorStaffId, String correlationId);

    static LabReleaseAuthorityPort denyAll() {
        return (account, staff, correlationId) -> false;
    }
}
