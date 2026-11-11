package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record RecordHandoverDTO(
        UUID handoverId,
        UUID recordId,
        UUID fromDoctorId,
        UUID toDoctorId,
        String reason,
        String pendingItems,
        String status,
        Instant requestedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant acceptedAt
) {
}
