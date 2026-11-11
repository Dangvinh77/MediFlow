package com.mediflow.lab.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;

/** {@code lab.result.amended} v1: the current revision replacing {@code previousResultVersion}. */
public record LabResultAmendedPayload(
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String labType,
        int resultVersion,
        int previousResultVersion,
        String amendmentReason,
        List<LabResultCreatedEvent.Result> results,
        List<LabResultV2Payload.ReleasedValue> values,
        String conclusion,
        UUID releasedBy,
        Instant releasedAt,
        String templateCode,
        int templateVersion,
        LocalDate performedDate
) {
    public LabResultAmendedPayload {
        results = List.copyOf(results);
        values = List.copyOf(values);
    }
}
