package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Tests durable outbox acknowledgement and retry behavior without a live broker. */
class PharmacyOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PharmacyOutboxClaimService claimService = mock(PharmacyOutboxClaimService.class);
    private final PharmacyOutboxDispatcher dispatcher = new PharmacyOutboxDispatcher(
            rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC), 100, 5000,
            claimService, 1000, 60000, 3, new SimpleMeterRegistry());

    /** A broker ACK is required before the row can be marked published. */
    @Test
    void dispatchPending_brokerAcknowledges_marksPublished() {
        PharmacyEventOutboxJpaEntity event = event("prescription.created");
        when(claimService.claim(any(Integer.class), any(String.class))).thenReturn(List.of(event));
        when(claimService.markPublished(eq(event.getEventId()), any(String.class), eq(NOW)))
                .thenReturn(true);
        completePublishWith(true, null);

        dispatcher.dispatchPending();

        verify(claimService).markPublished(eq(event.getEventId()), any(String.class), eq(NOW));
        verify(claimService, never()).markFailure(any(), any(), any(), any(), any(Integer.class));
    }

    /** Dispatch sends the immutable outbox payload, including the producer-owned recordId. */
    @Test
    void dispatchPending_filledPayloadPreservesRecordId() {
        String payload = "{\"prescriptionId\":\"55555555-5555-5555-5555-555555555555\","
                + "\"recordId\":\"66666666-6666-6666-6666-666666666666\"}";
        PharmacyEventOutboxJpaEntity event = new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), "prescription.filled", UUID.randomUUID(), payload);
        when(claimService.claim(any(Integer.class), any(String.class))).thenReturn(List.of(event));
        when(claimService.markPublished(eq(event.getEventId()), any(String.class), eq(NOW)))
                .thenReturn(true);
        completePublishWith(true, null);

        dispatcher.dispatchPending();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq("mediflow.events"), eq("prescription.filled"), messageCaptor.capture(),
                any(CorrelationData.class));
        assertThat(new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo(payload);
    }

    /** A broker NACK keeps a critical row pending and stops later events from overtaking it. */
    @Test
    void dispatchPending_brokerRejects_recordsFailureAndStopsBatch() {
        PharmacyEventOutboxJpaEntity first = event("prescription.created");
        PharmacyEventOutboxJpaEntity second = event("prescription.filled");
        when(claimService.claim(any(Integer.class), any(String.class)))
                .thenReturn(List.of(first, second));
        completePublishWith(false, "broker rejected message");

        dispatcher.dispatchPending();

        verify(claimService).markFailure(eq(first.getEventId()), any(String.class), any(), any(), eq(3));
        verify(claimService, never()).markPublished(any(), any(), any());
        verify(rabbitTemplate, times(1)).send(
                eq("mediflow.events"), any(), any(Message.class), any(CorrelationData.class));
    }

    /** A non-critical stock notification must not block a later saga completion event. */
    @Test
    void dispatchPending_stockLowFailure_doesNotBlockLaterEvent() {
        PharmacyEventOutboxJpaEntity stockLow = event("stock.low");
        PharmacyEventOutboxJpaEntity filled = event("prescription.filled");
        when(claimService.claim(any(Integer.class), any(String.class)))
                .thenReturn(List.of(stockLow, filled));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            Message message = invocation.getArgument(2);
            boolean ack = message.getMessageProperties().getMessageId().equals(filled.getEventId().toString());
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "stock warning failed"));
            return null;
        }).when(rabbitTemplate).send(
                eq("mediflow.events"), any(), any(Message.class), any(CorrelationData.class));
        when(claimService.markPublished(eq(filled.getEventId()), any(String.class), eq(NOW)))
                .thenReturn(true);

        dispatcher.dispatchPending();

        verify(claimService).markFailure(eq(stockLow.getEventId()), any(String.class), any(), any(), eq(3));
        verify(claimService).markPublished(eq(filled.getEventId()), any(String.class), eq(NOW));
        verify(rabbitTemplate, times(2)).send(
                eq("mediflow.events"), any(), any(Message.class), any(CorrelationData.class));
    }

    /** The final failed attempt is marked with the bounded retry policy for quarantine. */
    @Test
    void dispatchPending_finalAttempt_schedulesQuarantine() {
        PharmacyEventOutboxJpaEntity event = event("prescription.created");
        event.setAttempts(2);
        when(claimService.claim(any(Integer.class), any(String.class))).thenReturn(List.of(event));
        completePublishWith(false, "broker rejected message");

        dispatcher.dispatchPending();

        ArgumentCaptor<Instant> availableAt = ArgumentCaptor.forClass(Instant.class);
        verify(claimService).markFailure(
                eq(event.getEventId()), any(String.class), any(), availableAt.capture(), eq(3));
        assertThat(availableAt.getValue()).isAfter(NOW);
    }

    /** A broker confirm timeout records a retryable failure and never marks the event published. */
    @Test
    void dispatchPending_confirmTimeout_recordsFailure() {
        PharmacyEventOutboxJpaEntity event = event("prescription.created");
        when(claimService.claim(any(Integer.class), any(String.class))).thenReturn(List.of(event));
        PharmacyOutboxDispatcher timeoutDispatcher = new PharmacyOutboxDispatcher(
                rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC), 100, 10,
                claimService, 1000, 60000, 3, new SimpleMeterRegistry());

        timeoutDispatcher.dispatchPending();

        verify(claimService).markFailure(eq(event.getEventId()), any(String.class), any(), any(), eq(3));
        verify(claimService, never()).markPublished(any(), any(), any());
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
        return new PharmacyEventOutboxJpaEntity(
                UUID.randomUUID(), routingKey, UUID.randomUUID(), "{\"eventId\":\"test\"}");
    }
}
