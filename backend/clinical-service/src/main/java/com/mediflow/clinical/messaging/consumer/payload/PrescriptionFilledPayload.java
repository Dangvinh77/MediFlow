package com.mediflow.clinical.messaging.consumer.payload;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Clinical projection of Pharmacy's {@code prescription.filled} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionFilledPayload(
        UUID eventId,
        UUID recordId,
        UUID prescriptionId
) {}
