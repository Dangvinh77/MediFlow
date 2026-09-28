package com.mediflow.clinical.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Versioned Lab event envelope projected to the fields Clinical owns and consumes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LabResultCreatedEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        Payload payload
) {
    public LabResultCreatedPayload toProjection() {
        return new LabResultCreatedPayload(eventId, occurredAt, correlationId,
                payload.labId(), payload.patientId(), payload.recordId(), payload.departmentId(),
                payload.labType(), payload.performedDate(), payload.results(), payload.conclusion());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID labId,
            UUID patientId,
            UUID recordId,
            UUID departmentId,
            String labType,
            LocalDate performedDate,
            List<LabResultCreatedPayload.Result> results,
            String conclusion
    ) {
    }
}
