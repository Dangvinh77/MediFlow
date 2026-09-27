package com.mediflow.lab.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabOutboxEventJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabOutboxEventJpaRepository;

/** Appends the event JSON in the same transaction as the Lab aggregate. */
@Component
public class LabOutboxPersistenceAdapter implements LabOutboxPort {

    private final LabOutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    public LabOutboxPersistenceAdapter(LabOutboxEventJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void append(DomainEventEnvelope<?> event) {
        try {
            JsonNode payload = objectMapper.valueToTree(event);
            UUID aggregateId = payload.path("labId").isTextual()
                    ? UUID.fromString(payload.path("labId").asText())
                    : payload.path("payload").path("labId").isTextual()
                    ? UUID.fromString(payload.path("payload").path("labId").asText()) : null;
            if (aggregateId == null) {
                throw new IllegalArgumentException("Lab outbox payload must include labId");
            }
            repository.save(LabOutboxEventJpaEntity.builder()
                    .eventId(event.eventId())
                    .aggregateType("LAB_TEST")
                    .aggregateId(aggregateId)
                    .eventType(event.eventType())
                    .eventVersion(event.version())
                    .correlationId(event.correlationId())
                    .payload(payload)
                    .occurredAt(event.occurredAt())
                    .retryCount(0)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize Lab outbox event", exception);
        }
    }
}
