package com.mediflow.clinical.application.dto.command;

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
        Integer resultVersion
) {
    public LabResultCreatedCommand(UUID eventId, UUID recordId, UUID labId, String conclusion) {
        this(eventId, recordId, labId, conclusion, 0, null, null, null, null, null);
    }
}
