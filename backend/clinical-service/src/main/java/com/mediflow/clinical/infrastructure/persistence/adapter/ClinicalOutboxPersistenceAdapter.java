package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.LegacyClinicalEventMetadata;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;
import com.mediflow.clinical.infrastructure.messaging.ClinicalOutpatientRegistrationEventSerializer;

@Component
public class ClinicalOutboxPersistenceAdapter implements ClinicalOutboxPort {
    private final ClinicalOutboxJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final ClinicalOutpatientRegistrationEventSerializer outpatientSerializer;

    @Autowired
    public ClinicalOutboxPersistenceAdapter(ClinicalOutboxJpaRepository repository, ObjectMapper objectMapper,
            ClinicalOutpatientRegistrationEventSerializer outpatientSerializer) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.outpatientSerializer = outpatientSerializer;
    }

    public ClinicalOutboxPersistenceAdapter(ClinicalOutboxJpaRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, new ClinicalOutpatientRegistrationEventSerializer(objectMapper));
    }

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

    @Override
    @Transactional
    public void appendRawOutpatientEvent(DomainEventEnvelope<?> event) {
        Objects.requireNonNull(event, "Clinical outpatient event is required");
        if (event.version() != 1 || event.correlationId() == null || event.correlationId().isBlank()
                || !"clinical-service".equals(event.producer())
                || !("outpatient.registration.changed".equals(event.eventType())
                || "outpatient.financial.eligibility.granted".equals(event.eventType()))) {
            throw new IllegalArgumentException("Unsupported Clinical outpatient event envelope");
        }
        byte[] wirePayload = outpatientSerializer.serialize(event);
        JsonNode payload;
        try {
            payload = objectMapper.reader()
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(wirePayload);
        } catch (IOException malformedPayload) {
            throw new IllegalArgumentException("Clinical outpatient event must be valid JSON", malformedPayload);
        }
        repository.save(ClinicalOutboxEventJpaEntity.builder()
                .eventId(event.eventId())
                .aggregateType(aggregateType(event.eventType()))
                .aggregateId(aggregateId(event, payload))
                .eventType(event.eventType())
                .eventVersion(event.version())
                .correlationId(event.correlationId())
                .payload(payload)
                .payloadBytes(wirePayload)
                .occurredAt(event.occurredAt())
                .retryCount(0)
                .build());
    }

    @Override
    @Transactional
    public void appendLegacyEvent(LegacyClinicalEventMetadata metadata, byte[] wirePayload) {
        Objects.requireNonNull(metadata, "legacy Clinical event metadata is required");
        Objects.requireNonNull(wirePayload, "legacy Clinical event wire payload is required");
        if (metadata.correlationId() == null) {
            throw new IllegalArgumentException("legacy Clinical event correlationId is required");
        }

        JsonNode payload;
        try {
            payload = objectMapper.reader()
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(wirePayload);
        } catch (IOException malformedPayload) {
            throw new IllegalArgumentException("legacy Clinical event wire payload must be valid JSON", malformedPayload);
        }
        validateFlatPayload(metadata, payload);

        repository.save(ClinicalOutboxEventJpaEntity.builder()
                .eventId(metadata.eventId())
                .aggregateType(legacyAggregateType(metadata.eventType()))
                .aggregateId(metadata.aggregateId())
                .eventType(metadata.eventType())
                .eventVersion(0)
                .correlationId(metadata.correlationId())
                .payload(payload)
                .payloadBytes(wirePayload.clone())
                .occurredAt(metadata.occurredAt())
                .retryCount(0)
                .build());
    }

    private static void validateFlatPayload(LegacyClinicalEventMetadata metadata, JsonNode payload) {
        if (!payload.isObject()
                || payload.has("payload")
                || !matches(payload, "eventId", metadata.eventId().toString())
                || !instantMatches(payload.path("occurredAt"), metadata.occurredAt())
                || !matches(payload, "correlationId", metadata.correlationId())
                || !matches(payload, legacyAggregateIdField(metadata.eventType()), metadata.aggregateId().toString())) {
            throw new IllegalArgumentException("legacy Clinical wire payload does not match its flat event metadata");
        }
    }

    private static String legacyAggregateIdField(String eventType) {
        return switch (eventType) {
            case "appointment.created", "appointment.status.changed" -> "appointmentId";
            case "medicalrecord.created", "diagnosis.added" -> "recordId";
            default -> throw new IllegalArgumentException("Unsupported flat Clinical event type: " + eventType);
        };
    }

    private static String legacyAggregateType(String eventType) {
        return switch (eventType) {
            case "appointment.created", "appointment.status.changed" -> "APPOINTMENT";
            case "medicalrecord.created", "diagnosis.added" -> "MEDICAL_RECORD";
            default -> throw new IllegalArgumentException("Unsupported flat Clinical event type: " + eventType);
        };
    }

    private static boolean instantMatches(JsonNode actual, Instant expected) {
        if (actual.isNumber()) {
            BigDecimal expectedEpoch = BigDecimal.valueOf(expected.getEpochSecond())
                    .add(BigDecimal.valueOf(expected.getNano(), 9));
            return actual.decimalValue().compareTo(expectedEpoch) == 0;
        }
        return actual.isTextual() && expected.toString().equals(actual.asText());
    }

    private static boolean matches(JsonNode payload, String field, String expected) {
        return expected.equals(payload.path(field).asText(null));
    }

    private static String aggregateType(String eventType) {
        if (eventType.startsWith("outpatient.")) {
            return "OUTPATIENT_REGISTRATION";
        }
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
            case "appointment.status.changed", "appointment.exam.cancelled" -> "appointmentId";
            case "outpatient.registration.changed", "outpatient.financial.eligibility.granted" -> "registrationId";
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
