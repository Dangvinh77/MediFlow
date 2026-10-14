package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
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

    @Test
    void dispatchSendsTheStoredFlatWireBytesWithoutReserializingAndUsesTheEventIdForConfirmation() {
        byte[] expectedWire = "{\"eventId\":\"legacy-flat-event\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ClinicalOutboxEventJpaEntity event = event();
        event.setPayloadBytes(expectedWire.clone());
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        dispatcher.dispatch();

        org.mockito.ArgumentCaptor<Message> message = org.mockito.ArgumentCaptor.forClass(Message.class);
        org.mockito.ArgumentCaptor<CorrelationData> correlation =
                org.mockito.ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(eq(RabbitConfig.EVENTS_EXCHANGE), eq(event.getEventType()),
                message.capture(), correlation.capture());
        assertThat(message.getValue().getBody()).containsExactly(expectedWire);
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(correlation.getValue().getId()).isEqualTo(event.getEventId().toString());
        assertThat(event.getPublishedAt()).isNotNull();
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class),
                any(CorrelationData.class));
    }

    @Test
    void outpatientMvpDispatcherHoldsDeferredRowsAndStillDispatchesEligibleAppointmentRows() {
        ClinicalOutboxEventJpaEntity appointment = event();
        byte[] wire = "{\"eventId\":\"active-appointment\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        appointment.setPayloadBytes(wire);
        when(outbox.lockUnpublishedForOutpatientMvp(100)).thenReturn(List.of(appointment));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        ClinicalOutboxDispatcher mvpDispatcher = new ClinicalOutboxDispatcher(
                outbox, rabbitTemplate, Duration.ofMillis(20), 5, new OutpatientMvpMode(true));

        mvpDispatcher.dispatch();

        verify(outbox).holdDeferredPendingEvents(any(Instant.class), any(String.class));
        verify(outbox).lockUnpublishedForOutpatientMvp(100);
        verify(outbox, never()).lockUnpublished(100);
        assertThat(appointment.getPublishedAt()).isNotNull();
        verify(rabbitTemplate).send(eq(RabbitConfig.EVENTS_EXCHANGE), eq(appointment.getEventType()),
                any(Message.class), any(CorrelationData.class));
    }

    @Test
    void dispatcherQuarantinesAfterConfiguredRetryBoundAndStopsResendingTheSameRow() {
        ClinicalOutboxEventJpaEntity event = event();
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), (Object) any(),
                        any(CorrelationData.class));
        ClinicalOutboxDispatcher boundedDispatcher = new ClinicalOutboxDispatcher(
                outbox, rabbitTemplate, Duration.ofMillis(20), 2, new OutpatientMvpMode(false));

        boundedDispatcher.dispatch();
        boundedDispatcher.dispatch();
        boundedDispatcher.dispatch();

        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getQuarantinedAt()).isNotNull();
        assertThat(event.getQuarantineReason()).isNotBlank();
        verify(rabbitTemplate, times(2)).convertAndSend(any(String.class), any(String.class), (Object) any(),
                any(CorrelationData.class));
        verify(outbox, times(2)).save(event);
    }

    @Test
    void dispatcherQuarantinesMigratedRowsAlreadyAtTheRetryLimitWithoutAnotherPublishAttempt() {
        ClinicalOutboxEventJpaEntity event = event();
        event.setRetryCount(2);
        when(outbox.lockUnpublished(100)).thenReturn(List.of(event));
        ClinicalOutboxDispatcher boundedDispatcher = new ClinicalOutboxDispatcher(
                outbox, rabbitTemplate, Duration.ofMillis(20), 2, new OutpatientMvpMode(false));

        boundedDispatcher.dispatch();

        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getQuarantinedAt()).isNotNull();
        assertThat(event.getQuarantineReason()).isNotBlank();
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), (Object) any(),
                any(CorrelationData.class));
        verify(outbox).save(event);
    }

    @Test
    void interruptionCountsOnlyUnconfirmedRowsAndKeepsCompletedAckSuccessful() {
        ClinicalOutboxEventJpaEntity acknowledged = event();
        ClinicalOutboxEventJpaEntity pending = event();
        when(outbox.lockUnpublished(100)).thenReturn(List.of(acknowledged, pending));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (correlation.getId().equals(acknowledged.getEventId().toString())) {
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), (Object) any(),
                any(CorrelationData.class));
        Thread.interrupted();

        try {
            Thread.currentThread().interrupt();
            dispatcher.dispatch();
        } finally {
            Thread.interrupted();
        }

        assertThat(acknowledged.getPublishedAt()).isNotNull();
        assertThat(acknowledged.getRetryCount()).isZero();
        assertThat(pending.getPublishedAt()).isNull();
        assertThat(pending.getRetryCount()).isEqualTo(1);
        verify(outbox).save(acknowledged);
        verify(outbox).save(pending);
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
