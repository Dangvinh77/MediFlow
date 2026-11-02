package com.mediflow.report.application.port.out;

import java.util.UUID;

/** Current Core-owned management authority; callers supply only a verified actor/credential. */
public interface CurrentReportAuthorityPort {
    boolean permits(UUID accountId, String role, UUID departmentId, String bearer, String correlationId);
}
