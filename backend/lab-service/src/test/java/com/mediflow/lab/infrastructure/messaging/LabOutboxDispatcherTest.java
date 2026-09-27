package com.mediflow.lab.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabOutboxEventJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabOutboxEventJpaRepository;

class LabOutboxDispatcherTest {

    private final LabOutboxEventJpaRepository repository = mock(LabOutboxEventJpaRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void dispatchPending_marksPublishedOnlyAfterBrokerAck() {
        LabOutboxEventJpaEntity event = pendingEvent();
        when(repository.lockNextBatch()).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                any(Message.class), any(CorrelationData.class));
        LabOutboxDispatcher dispatcher = new LabOutboxDispatcher(repository, rabbitTemplate, 5);

        dispatcher.dispatchPending();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getRetryCount()).isZero();
        verify(rabbitTemplate).send(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                any(Message.class), any(CorrelationData.class));
    }

    @Test
    void dispatchPending_nackKeepsEventPendingAndIncrementsRetryCount() {
        LabOutboxEventJpaEntity event = pendingEvent();
        when(repository.lockNextBatch()).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                any(Message.class), any(CorrelationData.class));
        LabOutboxDispatcher dispatcher = new LabOutboxDispatcher(repository, rabbitTemplate, 5);

        dispatcher.dispatchPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    void dispatchPending_returnedMessageKeepsEventPending() {
        LabOutboxEventJpaEntity event = pendingEvent();
        when(repository.lockNextBatch()).thenReturn(List.of(event));
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE",
                    RabbitConfig.EVENTS_EXCHANGE, event.getEventType()));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                any(Message.class), any(CorrelationData.class));
        LabOutboxDispatcher dispatcher = new LabOutboxDispatcher(repository, rabbitTemplate, 5);

        dispatcher.dispatchPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    void dispatchPending_confirmTimeoutKeepsEventPending() {
        LabOutboxEventJpaEntity event = pendingEvent();
        when(repository.lockNextBatch()).thenReturn(List.of(event));
        LabOutboxDispatcher dispatcher = new LabOutboxDispatcher(repository, rabbitTemplate, 5);

        dispatcher.dispatchPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    private static LabOutboxEventJpaEntity pendingEvent() {
        return LabOutboxEventJpaEntity.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("LAB_TEST")
                .aggregateId(UUID.randomUUID())
                .eventType("lab.request.created")
                .eventVersion(1)
                .correlationId(UUID.randomUUID().toString())
                .payload(JsonNodeFactory.instance.objectNode().put("labId", UUID.randomUUID().toString()))
                .occurredAt(Instant.now())
                .retryCount(0)
                .build();
    }
}
