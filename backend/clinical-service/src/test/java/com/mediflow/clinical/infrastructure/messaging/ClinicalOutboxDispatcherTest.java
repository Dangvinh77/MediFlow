package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

class ClinicalOutboxDispatcherTest {

    private final ClinicalOutboxJpaRepository outbox = mock(ClinicalOutboxJpaRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private ClinicalOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ClinicalOutboxDispatcher(outbox, rabbitTemplate, Duration.ofMillis(20));
    }

    @Test
    void dispatchMarksEventPublishedOnlyAfterCorrelatedBrokerAck() {
        ClinicalOutboxEventJpaEntity event = event();
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));
        completeWithConfirm(true, null);

        dispatcher.dispatch();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getRetryCount()).isZero();
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                same(event.getPayload()), any(CorrelationData.class));
        verify(outbox).save(event);
    }

    @Test
    void dispatchLeavesNackedEventUnpublishedAndIncrementsRetryCount() {
        ClinicalOutboxEventJpaEntity event = event();
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));
        completeWithConfirm(false, "broker rejected publication");

        dispatcher.dispatch();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outbox).save(event);
    }

    @Test
    void dispatchLeavesReturnedEventUnpublishedEvenWhenBrokerAckedIt() {
        ClinicalOutboxEventJpaEntity event = event();
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", RabbitConfig.EVENTS_EXCHANGE, event.getEventType()));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                same(event.getPayload()), any(CorrelationData.class));

        dispatcher.dispatch();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outbox).save(event);
    }

    @Test
    void dispatchRetriesEventWhenBrokerConfirmTimesOut() {
        ClinicalOutboxEventJpaEntity event = event();
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));

        dispatcher.dispatch();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outbox).save(event);
    }

    private void completeWithConfirm(boolean acknowledged, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), (Object) any(),
                any(CorrelationData.class));
    }

    private ClinicalOutboxEventJpaEntity event() {
        return ClinicalOutboxEventJpaEntity.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("APPOINTMENT")
                .aggregateId(UUID.randomUUID())
                .eventType("appointment.created")
                .eventVersion(1)
                .correlationId("correlation-test")
                .payload(JsonNodeFactory.instance.objectNode())
                .occurredAt(Instant.now())
                .retryCount(0)
                .build();
    }
}
