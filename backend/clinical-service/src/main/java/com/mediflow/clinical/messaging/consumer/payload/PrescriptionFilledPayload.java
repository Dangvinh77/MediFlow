package com.mediflow.clinical.messaging.consumer.payload;

import java.util.UUID;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Clinical projection of Pharmacy's {@code prescription.filled} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionFilledPayload(
        UUID eventId,
        UUID recordId,
        UUID prescriptionId,
        int sourceVersion,
        UUID patientId,
        UUID departmentId,
        String careEpisodeType,
        UUID careEpisodeId,
        UUID dispenseId,
        Instant filledAt
) {
    public PrescriptionFilledPayload(UUID eventId, UUID recordId, UUID prescriptionId) {
        this(eventId, recordId, prescriptionId, 0, null, null, null, null, null, null);
    }
}
