package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DepartmentHeadSnapshot(
        UUID departmentId,
        long revision,
        UUID appointmentId,
        UUID staffId,
        UUID accountId,
        Instant updatedAt,
        UUID updatedByAccountId,
        List<Term> history) {
    public record Term(
            UUID appointmentId, UUID staffId, UUID accountId, Instant startedAt, Instant endedAt) {}
}
