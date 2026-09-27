package com.mediflow.lab.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;

/** Verified result snapshot for a V2 Lab test. */
public record LabResultV2Payload(
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String labType,
        int resultVersion,
        List<LabResultCreatedEvent.Result> results,
        String conclusion,
        UUID verifiedBy,
        LocalDate performedDate,
        Instant completedAt
) {
    public LabResultV2Payload {
        results = results == null ? null : List.copyOf(results);
    }
}
