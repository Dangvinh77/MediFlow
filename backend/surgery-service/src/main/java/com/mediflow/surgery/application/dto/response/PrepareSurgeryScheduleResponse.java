package com.mediflow.surgery.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PrepareSurgeryScheduleResponse(UUID surgeryCaseId, long caseRevision,
        UUID scheduleId, long scheduleRevision, String scheduleStatus, Instant occurredAt, boolean replayed) {}
