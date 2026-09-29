package com.mediflow.surgery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SurgeryOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-29T06:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);

    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final SurgeryOutboxPort outbox = mock(SurgeryOutboxPort.class);
    private final SurgeryEventPublisherPort publisher = mock(SurgeryEventPublisherPort.class);
    private final SurgeryClockPort clock = () -> NOW;
    private final SurgeryOutboxDispatcher dispatcher =
            new SurgeryOutboxDispatcher(transactions, outbox, publisher, clock, LEASE, 20);

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void runTransactionCallbacksImmediately() {
        doAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactions).execute(any(TransactionCallback.class));
    }

    @Test
    void dispatchOne_noPendingEvent_doesNotCallPublisher() {
        when(outbox.claimNext(NOW, LEASE)).thenReturn(Optional.empty());

        assertThat(dispatcher.dispatchOne()).isFalse();

        verifyNoInteractions(publisher);
    }

    @Test
    void dispatchOne_commitsClaimBeforePublishAndRecordsConfirmAfterward() {
        SurgeryOutboxPort.Delivery delivery = delivery();
        AtomicInteger transactionCount = new AtomicInteger();
        when(outbox.claimNext(NOW, LEASE)).thenReturn(Optional.of(delivery));
        when(outbox.markPublished(delivery.eventId(), delivery.attemptToken(), NOW)).thenReturn(true);
        doAnswer(invocation -> {
            assertThat(transactionCount.get()).isEqualTo(1);
            SurgeryEventPublisherPort.OutgoingMessage message = invocation.getArgument(0);
            assertThat(message.eventId()).isEqualTo(delivery.eventId());
            assertThat(message.eventType()).isEqualTo("surgery.ready");
            assertThat(message.payload()).containsExactly(delivery.payload());
            return null;
        }).when(publisher).publish(any());
        doAnswer(invocation -> {
            transactionCount.incrementAndGet();
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactions).execute(any());

        assertThat(dispatcher.dispatchOne()).isTrue();

        assertThat(transactionCount).hasValue(2);
        verify(outbox).markPublished(delivery.eventId(), delivery.attemptToken(), NOW);
    }

    @Test
    void dispatchOne_unroutableEventRecordsReturnWithoutMarkingPublished() {
        SurgeryOutboxPort.Delivery delivery = delivery();
        when(outbox.claimNext(NOW, LEASE)).thenReturn(Optional.of(delivery));
        when(outbox.markReturned(delivery.eventId(), delivery.attemptToken(), "unroutable", NOW))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new SurgeryEventPublishException("unroutable", true))
                .when(publisher).publish(any());

        assertThat(dispatcher.dispatchOne()).isTrue();

        verify(outbox).markReturned(delivery.eventId(), delivery.attemptToken(), "unroutable", NOW);
        org.mockito.Mockito.verify(outbox, org.mockito.Mockito.never())
                .markPublished(delivery.eventId(), delivery.attemptToken(), NOW);
    }

    @Test
    void dispatchOne_nackRecordsBoundedRetry() {
        SurgeryOutboxPort.Delivery delivery = delivery();
        when(outbox.claimNext(NOW, LEASE)).thenReturn(Optional.of(delivery));
        when(outbox.retry(delivery.eventId(), delivery.attemptToken(), "broker nack", NOW)).thenReturn(true);
        org.mockito.Mockito.doThrow(new SurgeryEventPublishException("broker nack", false))
                .when(publisher).publish(any());

        assertThat(dispatcher.dispatchOne()).isTrue();

        verify(outbox).retry(delivery.eventId(), delivery.attemptToken(), "broker nack", NOW);
    }

    @Test
    void dispatchOne_transientBrokerFailureRecordsRetryInsteadOfLosingEvent() {
        SurgeryOutboxPort.Delivery delivery = delivery();
        when(outbox.claimNext(NOW, LEASE)).thenReturn(Optional.of(delivery));
        when(outbox.retry(delivery.eventId(), delivery.attemptToken(),
                "RabbitMQ publish failed", NOW)).thenReturn(true);
        org.mockito.Mockito.doThrow(new SurgeryEventPublishException(
                        "RabbitMQ publish failed", new IllegalStateException("connection refused")))
                .when(publisher).publish(any());

        assertThat(dispatcher.dispatchOne()).isTrue();

        verify(outbox).retry(delivery.eventId(), delivery.attemptToken(),
                "RabbitMQ publish failed", NOW);
        org.mockito.Mockito.verify(outbox, org.mockito.Mockito.never())
                .markPublished(delivery.eventId(), delivery.attemptToken(), NOW);
    }

    private static SurgeryOutboxPort.Delivery delivery() {
        return new SurgeryOutboxPort.Delivery(
                UUID.randomUUID(), UUID.randomUUID(), 3, 0, "surgery.ready", 1,
                "correlation-1", new byte[] {1, 2, 3}, UUID.randomUUID(), NOW.plus(LEASE), 1);
    }
}
