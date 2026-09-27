package com.mediflow.clinical.infrastructure.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

@Component
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ClinicalOutboxDispatcher.class);

    private final ClinicalOutboxJpaRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmationTimeout;

    public ClinicalOutboxDispatcher(
            ClinicalOutboxJpaRepository outbox,
            RabbitTemplate rabbitTemplate,
            @Value("${mediflow.clinical.outbox-confirm-timeout:5s}") Duration confirmationTimeout) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmationTimeout = confirmationTimeout;
    }

    @Scheduled(fixedDelayString = "${mediflow.clinical.outbox-poll-interval:1000}")
    @Transactional
    public void dispatch() {
        List<PendingPublication> pending = new ArrayList<>();
        for (ClinicalOutboxEventJpaEntity event : outbox.lockUnpublished(100)) {
            try {
                CorrelationData correlationData = new CorrelationData(event.getEventId().toString());
                rabbitTemplate.convertAndSend(
                        RabbitConfig.EVENTS_EXCHANGE, event.getEventType(), event.getPayload(), correlationData);
                pending.add(new PendingPublication(event, correlationData));
            } catch (RuntimeException failure) {
                recordFailure(event, "send failed", failure);
                outbox.save(event);
            }
        }

        boolean interrupted = awaitConfirmations(pending);
        for (PendingPublication publication : pending) {
            ClinicalOutboxEventJpaEntity event = publication.event();
            if (interrupted) {
                recordFailure(event, "publisher confirmation interrupted", null);
            } else {
                applyConfirmation(publication);
            }
            outbox.save(event);
        }
    }

    private boolean awaitConfirmations(List<PendingPublication> pending) {
        if (pending.isEmpty()) {
            return false;
        }
        CompletableFuture<?>[] confirmations = pending.stream()
                .map(publication -> publication.correlationData().getFuture())
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(confirmations).get(confirmationTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return true;
        } catch (ExecutionException | TimeoutException ignored) {
            // Inspect each result below so successful publications are still recorded independently.
        }
        return false;
    }

    private void applyConfirmation(PendingPublication publication) {
        ClinicalOutboxEventJpaEntity event = publication.event();
        CompletableFuture<CorrelationData.Confirm> confirmation = publication.correlationData().getFuture();
        if (!confirmation.isDone()) {
            recordFailure(event, "publisher confirmation timed out", null);
            return;
        }

        CorrelationData.Confirm result;
        try {
            result = confirmation.join();
        } catch (CompletionException | CancellationException failure) {
            recordFailure(event, "publisher confirmation failed", failure);
            return;
        }

        if (!result.isAck()) {
            recordFailure(event, "broker nacked publication: " + result.getReason(), null);
            return;
        }

        ReturnedMessage returned = publication.correlationData().getReturned();
        if (returned != null) {
            recordFailure(event,
                    "broker returned publication " + returned.getReplyCode() + " " + returned.getReplyText(), null);
            return;
        }

        event.setPublishedAt(Instant.now());
    }

    private void recordFailure(ClinicalOutboxEventJpaEntity event, String reason, Throwable failure) {
        event.setRetryCount(event.getRetryCount() + 1);
        if (failure == null) {
            log.warn("Clinical outbox publish failed eventId={} eventType={} retryCount={} reason={}",
                    event.getEventId(), event.getEventType(), event.getRetryCount(), reason);
        } else {
            log.warn("Clinical outbox publish failed eventId={} eventType={} retryCount={} reason={}",
                    event.getEventId(), event.getEventType(), event.getRetryCount(), reason, failure);
        }
    }

    private record PendingPublication(
            ClinicalOutboxEventJpaEntity event,
            CorrelationData correlationData) {
    }
}
