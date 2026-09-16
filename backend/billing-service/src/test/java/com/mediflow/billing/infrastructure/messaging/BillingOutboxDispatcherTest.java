package com.mediflow.billing.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.BillingEventOutboxJpaEntity;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Verifies durable delivery acknowledgement, retry and causal batch stopping. */
class BillingOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-16T02:00:00Z");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final BillingOutboxClaimService claimService = mock(BillingOutboxClaimService.class);

    @Test
    void dispatchPending_brokerAcknowledges_marksPublished() {
        BillingEventOutboxJpaEntity event = event("payment.completed");
        when(claimService.claim(any(Integer.class), any(String.class))).thenReturn(List.of(event));
        when(claimService.markPublished(eq(event.getEventId()), any(String.class), eq(NOW)))
                .thenReturn(true);
        completePublishWith(true, null);

        dispatcher(5000).dispatchPending();

        verify(claimService).markPublished(eq(event.getEventId()), any(String.class), eq(NOW));
        verify(claimService, never()).markFailure(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void dispatchPending_brokerRejects_recordsFailureAndStopsBatch() {
        BillingEventOutboxJpaEntity first = event("invoice.created");
        BillingEventOutboxJpaEntity second = event("payment.completed");
        when(claimService.claim(any(Integer.class), any(String.class)))
                .thenReturn(List.of(first, second));
        completePublishWith(false, "rejected");

        dispatcher(5000).dispatchPending();

        verify(claimService).markFailure(eq(first.getEventId()), any(String.class), any(), any(), eq(3));
        verify(rabbitTemplate, times(1)).send(eq("mediflow.events"), any(),
                any(Message.class), any(CorrelationData.class));
    }

    @Test
    void dispatchPending_confirmTimeout_keepsEventPending() {
        BillingEventOutboxJpaEntity event = event("payment.failed");
        when(claimService.claim(any(Integer.class), any(String.class))).thenReturn(List.of(event));

        dispatcher(10).dispatchPending();

        verify(claimService).markFailure(eq(event.getEventId()), any(String.class), any(), any(), eq(3));
        verify(claimService, never()).markPublished(any(), any(), any());
    }

    private BillingOutboxDispatcher dispatcher(long confirmTimeoutMs) {
        return new BillingOutboxDispatcher(rabbitTemplate, claimService,
                Clock.fixed(NOW, ZoneOffset.UTC), 100, confirmTimeoutMs,
                1000, 60000, 3, new SimpleMeterRegistry());
    }

    private void completePublishWith(boolean acknowledged, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
            return null;
        }).when(rabbitTemplate).send(eq("mediflow.events"), any(),
                any(Message.class), any(CorrelationData.class));
    }

    private BillingEventOutboxJpaEntity event(String routingKey) {
        return new BillingEventOutboxJpaEntity(
                UUID.randomUUID(), routingKey, UUID.randomUUID(), "{\"eventId\":\"test\"}");
    }
}
