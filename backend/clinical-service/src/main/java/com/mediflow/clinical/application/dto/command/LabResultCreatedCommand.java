package com.mediflow.clinical.application.dto.command;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.CareEpisodeType;

/** Application projection of the fields Clinical uses from lab.result.created. */
public record LabResultCreatedCommand(
        UUID eventId,
        UUID recordId,
        UUID labId,
        String conclusion,
        int sourceVersion,
        UUID patientId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        Integer resultVersion,
        /** When Lab produced the result; documents compare it, not the arrival time, with the visit's end. */
        Instant occurredAt
) {
    public LabResultCreatedCommand(UUID eventId, UUID recordId, UUID labId, String conclusion, int sourceVersion,
            UUID patientId, UUID departmentId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
            Integer resultVersion) {
        this(eventId, recordId, labId, conclusion, sourceVersion, patientId, departmentId, careEpisodeType,
                careEpisodeId, resultVersion, null);
    }

    public LabResultCreatedCommand(UUID eventId, UUID recordId, UUID labId, String conclusion) {
        this(eventId, recordId, labId, conclusion, 0, null, null, null, null, null, null);
    }
}
