package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.FollowUpRecommendationCreatedFact;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationOutboxPort;
import com.mediflow.clinical.infrastructure.messaging.FollowUpRecommendationEventSerializer;

@Component
public class FollowUpRecommendationOutboxPersistenceAdapter implements FollowUpRecommendationOutboxPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FollowUpRecommendationEventSerializer serializer;

    public FollowUpRecommendationOutboxPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper,
            FollowUpRecommendationEventSerializer serializer) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.serializer = serializer;
    }

    @Override
    @Transactional
    public void append(DomainEventEnvelope<FollowUpRecommendationCreatedFact> event) {
        validate(event);
        byte[] wirePayload = serializer.serialize(event);
        JsonNode payload;
        try {
            payload = objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(wirePayload);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Follow-up recommendation event must be valid JSON", malformed);
        }
        UUID aggregateId = event.payload().recommendationId();
        jdbc.update("""
                INSERT INTO clinical_outbox_event(event_id, aggregate_type, aggregate_id, event_type, event_version,
                    correlation_id, payload, payload_bytes, occurred_at, retry_count)
                VALUES (?, 'FOLLOW_UP_RECOMMENDATION', ?, ?, ?, ?, ?::jsonb, ?, ?, 0)
                """, event.eventId(), aggregateId, event.eventType(), event.version(), event.correlationId(),
                payload.toString(), wirePayload, Timestamp.from(event.occurredAt()));
    }

    private static void validate(DomainEventEnvelope<FollowUpRecommendationCreatedFact> event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
                || event.correlationId() == null || event.correlationId().isBlank()
                || !"clinical-service".equals(event.producer()) || event.version() != 1
                || !"followup.recommendation.created".equals(event.eventType()) || event.payload() == null
                || event.payload().recommendationId() == null || event.payload().sourceRecordId() == null
                || event.payload().sourceRegistrationId() == null || event.payload().patientId() == null
                || event.payload().departmentId() == null || event.payload().preferredDoctorId() == null
                || event.payload().recommendedFrom() == null || event.payload().recommendedThrough() == null
                || event.payload().recommendedThrough().isBefore(event.payload().recommendedFrom())) {
            throw new IllegalArgumentException("Invalid follow-up recommendation event envelope");
        }
    }
}
