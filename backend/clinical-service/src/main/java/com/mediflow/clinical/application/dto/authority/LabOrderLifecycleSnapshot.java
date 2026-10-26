package com.mediflow.clinical.application.dto.authority;

import java.time.Instant;
import java.util.UUID;

/** Exact read-only lifecycle binding returned by Lab for a Clinical-owned source order. */
public record LabOrderLifecycleSnapshot(
        UUID sourceOrderId,
        Boolean exists,
        UUID testId,
        UUID recordId,
        UUID patientId,
        UUID departmentId,
        String careEpisodeType,
        UUID careEpisodeId,
        String status,
        Instant observedAt) {}
