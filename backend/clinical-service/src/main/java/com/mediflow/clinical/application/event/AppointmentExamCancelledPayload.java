package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.CareEpisodeType;

/** Durable evidence that this exact outpatient examination target was cancelled. */
public record AppointmentExamCancelledPayload(
        UUID appointmentId,
        UUID patientId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String sourceType,
        UUID sourceId,
        UUID cancelledBy,
        String reason,
        Instant cancelledAt
) {}
