package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Minimal owned relationship facts, not a medication order or dispensing permission. */
public record PrescriptionContextDTO(boolean exists, UUID recordId, UUID patientId, UUID doctorId,
        UUID departmentId, String careEpisodeType, UUID careEpisodeId, String recordStatus,
        String disposition, Instant observedAt) {}
