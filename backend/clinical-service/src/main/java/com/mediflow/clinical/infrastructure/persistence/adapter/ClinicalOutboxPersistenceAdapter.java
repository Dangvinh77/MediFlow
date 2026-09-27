package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClinicalOutboxPersistenceAdapter implements ClinicalOutboxPort {
    private final ClinicalOutboxJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void append(DomainEventEnvelope<?> event) {
        JsonNode payload = objectMapper.valueToTree(event);
        repository.save(ClinicalOutboxEventJpaEntity.builder()
                .eventId(event.eventId())
                .aggregateType(aggregateType(event.eventType()))
                .aggregateId(aggregateId(event, payload))
                .eventType(event.eventType())
                .eventVersion(event.version())
                .correlationId(event.correlationId())
                .payload(payload)
                .occurredAt(event.occurredAt())
                .retryCount(0)
                .build());
    }

    private static String aggregateType(String eventType) {
        if (eventType.startsWith("appointment.")) {
            return "APPOINTMENT";
        }
        if ("admission.requested".equals(eventType)) {
            return "ADMISSION_REFERRAL";
        }
        return "MEDICAL_RECORD";
    }

    private static UUID aggregateId(DomainEventEnvelope<?> event, JsonNode envelope) {
        JsonNode payload = envelope.path("payload");
        String field = switch (event.eventType()) {
            case "appointment.status.changed" -> "appointmentId";
            case "admission.requested" -> "admissionRequestId";
            default -> "recordId";
        };
        String rawId = payload.path(field).asText(null);
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("Clinical outbox event must have an aggregate id");
        }
        return UUID.fromString(rawId);
    }
}
