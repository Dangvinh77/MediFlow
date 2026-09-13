package com.mediflow.pharmacy.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

/** Tests durable outbox acknowledgement and retry behavior without a live broker. */
class PharmacyOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    private final PharmacyEventOutboxJpaRepository repository =
            mock(PharmacyEventOutboxJpaRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PharmacyOutboxDispatcher dispatcher = new PharmacyOutboxDispatcher(
            repository, rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC), 100, 5000);

    /** A broker ACK is required before the row can be marked published. */
    @Test
    void dispatchPending_brokerAcknowledges_marksPublished() {
        PharmacyEventOutboxJpaEntity event = event("prescription.created");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        completePublishWith(true, null);

        dispatcher.dispatchPending();

        verify(repository).markPublished(event, NOW);
        verify(repository, never()).markFailure(eq(event), any());
    }

    /** A broker NACK keeps the row pending and stops later events from overtaking it. */
    @Test
    void dispatchPending_brokerRejects_recordsFailureAndStopsBatch() {
        PharmacyEventOutboxJpaEntity first = event("prescription.created");
        PharmacyEventOutboxJpaEntity second = event("prescription.filled");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(first, second));
        completePublishWith(false, "broker rejected message");

        dispatcher.dispatchPending();

        verify(repository).markFailure(eq(first), any());
        verify(repository, never()).markPublished(any(), any());
        verify(rabbitTemplate, times(1)).send(
                eq("mediflow.events"), any(), any(Message.class), any(CorrelationData.class));
    }

    private void completePublishWith(boolean acknowledged, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
            return null;
        }).when(rabbitTemplate).send(
                eq("mediflow.events"), any(), any(Message.class), any(CorrelationData.class));
    }

    private PharmacyEventOutboxJpaEntity event(String routingKey) {
        return new PharmacyEventOutboxJpaEntity(UUID.randomUUID(), routingKey, "{\"eventId\":\"test\"}");
    }
}
