package com.mediflow.lab.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Exact Clinical V1 medical-record event envelope and projection fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedicalRecordCreatedEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        Payload payload
) {
    public MedicalRecordCreatedPayload toProjection() {
        return new MedicalRecordCreatedPayload(eventId, occurredAt, correlationId,
                payload.recordId(), payload.patientId(), payload.doctorId(), payload.departmentId(),
                null, payload.examinationDate(), 1);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID recordId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            UUID departmentId,
            String careEpisodeType,
            UUID careEpisodeId,
            String sourceType,
            UUID sourceId,
            String priceCode,
            LocalDate examinationDate
    ) { }
}
