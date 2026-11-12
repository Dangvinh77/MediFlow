package com.mediflow.clinical.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mediflow.clinical.application.dto.command.LabResultAmendedCommand;
import com.mediflow.clinical.domain.model.CareEpisodeType;

/** {@code lab.result.amended} v1 as Lab publishes it; Clinical reads only what it shows. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LabResultAmendedEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        Payload payload
) {
    public static final String EVENT_TYPE = "lab.result.amended";

    /** Returns the reason the envelope is unusable, or null when it is well formed. */
    public String problem() {
        if (version != 1) return "version must be 1";
        if (!EVENT_TYPE.equals(eventType)) return "eventType must be lab.result.amended";
        if (!"lab-service".equals(producer)) return "producer must be lab-service";
        if (eventId == null || occurredAt == null || correlationId == null || correlationId.isBlank()
                || payload == null) return "required envelope fields are missing";
        Payload p = payload;
        if (p.labId() == null || p.patientId() == null || p.recordId() == null || p.departmentId() == null
                || p.careEpisodeId() == null || p.releasedAt() == null) return "required payload fields are missing";
        if (!"OUTPATIENT_VISIT".equals(p.careEpisodeType())) return "careEpisodeType is unsupported";
        if (p.resultVersion() == null || p.resultVersion() < 2) return "resultVersion must be 2 or more";
        if (p.previousResultVersion() == null || p.previousResultVersion() < 1
                || p.previousResultVersion() >= p.resultVersion()) return "previousResultVersion must precede resultVersion";
        if (p.amendmentReason() == null || p.amendmentReason().isBlank()) return "amendmentReason is required";
        return null;
    }

    public LabResultAmendedCommand toCommand() {
        return new LabResultAmendedCommand(eventId, payload.recordId(), payload.labId(), payload.patientId(),
                payload.departmentId(), CareEpisodeType.OUTPATIENT_VISIT, payload.careEpisodeId(),
                payload.resultVersion(), payload.previousResultVersion(), payload.amendmentReason().trim(),
                payload.conclusion(), payload.releasedAt());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID labId,
            UUID patientId,
            UUID recordId,
            UUID departmentId,
            String careEpisodeType,
            UUID careEpisodeId,
            Integer resultVersion,
            Integer previousResultVersion,
            String amendmentReason,
            String conclusion,
            Instant releasedAt
    ) {
    }
}
