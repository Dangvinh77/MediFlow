package com.mediflow.report.messaging.consumer.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire projection of pharmacy-service's prescription.filled event. */
public record PrescriptionFilledPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID departmentId,
        List<DispensedItemPayload> dispensedItems
) {

    public record DispensedItemPayload(UUID drugId, String drugName, int quantity) {
    }
}
