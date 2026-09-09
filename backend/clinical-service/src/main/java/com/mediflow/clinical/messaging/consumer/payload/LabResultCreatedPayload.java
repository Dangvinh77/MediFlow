package com.mediflow.clinical.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Local wire projection matching the Lab producer contract. */
public record LabResultCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        String labType,
        LocalDate performedDate,
        List<Result> results,
        String conclusion
) {
    public record Result(
            UUID resultId,
            String indicator,
            String value,
            String unit,
            String referenceRange
    ) {
    }
}
