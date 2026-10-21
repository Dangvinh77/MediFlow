package com.mediflow.billing.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OutpatientExecutionStartDTO(UUID operationId, UUID clearanceId, String sourceType,
        UUID sourceId, String state, Instant startedAt) { }
