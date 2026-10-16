package com.mediflow.clinical.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Pharmacy-owned V1 relationship; price/item fields remain in the raw inbox evidence. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionFilledEnvelope(UUID eventId, String eventType, int version, Instant occurredAt,
        String correlationId, String producer, Payload payload) {
    public PrescriptionFilledPayload toProjection() {
        return new PrescriptionFilledPayload(eventId, payload.recordId(), payload.prescriptionId(), 1,
                payload.patientId(), payload.departmentId(), payload.careEpisodeType(), payload.careEpisodeId(),
                payload.dispenseId(), payload.filledAt());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(UUID prescriptionId, UUID recordId, UUID patientId, UUID departmentId,
            String careContext, String careEpisodeType, UUID careEpisodeId, UUID admissionId,
            String sourceType, UUID sourceId, UUID dispenseId, Instant filledAt) { }
}
