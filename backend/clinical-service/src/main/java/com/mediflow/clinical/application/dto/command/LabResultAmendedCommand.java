package com.mediflow.clinical.application.dto.command;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.CareEpisodeType;

/** Clinical projection of {@code lab.result.amended} v1: a released correction of an earlier Lab result. */
public record LabResultAmendedCommand(
        UUID eventId,
        UUID recordId,
        UUID labId,
        UUID patientId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        int resultVersion,
        int previousResultVersion,
        String amendmentReason,
        String conclusion,
        Instant releasedAt) {
}
