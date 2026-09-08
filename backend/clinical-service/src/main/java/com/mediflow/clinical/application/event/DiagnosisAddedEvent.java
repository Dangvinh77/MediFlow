package com.mediflow.clinical.application.event;

import java.util.UUID;
import java.time.Instant;
import com.mediflow.clinical.domain.model.Diagnosis;
/** ICD code maps to the canonical diagnosisCode event field; publish after commit. */
public record DiagnosisAddedEvent(
        UUID eventId, Instant occurredAt, String correlationId,
        UUID recordId, String diagnosisCode, String diagnosisName
) {
    public static final String ROUTING_KEY = "diagnosis.added";

    public static DiagnosisAddedEvent from(UUID recordId, Diagnosis diagnosis, String correlationId) {
        return new DiagnosisAddedEvent(UUID.randomUUID(), Instant.now(), correlationId,
                recordId, diagnosis.getIcdCode(), diagnosis.getDiagnosisName());
    }
}
