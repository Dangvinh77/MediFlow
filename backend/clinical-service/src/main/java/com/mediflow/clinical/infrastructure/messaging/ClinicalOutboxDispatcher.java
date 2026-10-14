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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.infrastructure.config.ClinicalOutboxActivationCondition;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

@Component
@Conditional(ClinicalOutboxActivationCondition.class)
public class ClinicalOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ClinicalOutboxDispatcher.class);
    private static final String DEFERRED_FACT_HOLD_REASON = "Deferred admission fact retained during outpatient MVP";
    private static final String MAX_RETRIES_QUARANTINE_REASON = "Maximum Clinical outbox publish retries reached";

    private final ClinicalOutboxJpaRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmationTimeout;
    private final int maxRetries;
    private final OutpatientMvpMode outpatientMvpMode;

    @Autowired
    public ClinicalOutboxDispatcher(
            ClinicalOutboxJpaRepository outbox,
            RabbitTemplate rabbitTemplate,
            @Value("${mediflow.clinical.outbox-confirm-timeout:5s}") Duration confirmationTimeout,
            @Value("${mediflow.clinical.outbox-max-retries:5}") int maxRetries,
            OutpatientMvpMode outpatientMvpMode) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("Clinical outbox max retries must be greater than zero");
        }
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmationTimeout = confirmationTimeout;
        this.maxRetries = maxRetries;
        this.outpatientMvpMode = outpatientMvpMode;
    }

    ClinicalOutboxDispatcher(
            ClinicalOutboxJpaRepository outbox,
            RabbitTemplate rabbitTemplate,
            Duration confirmationTimeout) {
        this(outbox, rabbitTemplate, confirmationTimeout, 5, new OutpatientMvpMode(false));
    }

    @Scheduled(fixedDelayString = "${mediflow.clinical.outbox-poll-interval:1000}")
    @Transactional
    public void dispatch() {
        if (outpatientMvpMode.enabled()) {
            outbox.holdDeferredPendingEvents(Instant.now(), DEFERRED_FACT_HOLD_REASON);
        }
        List<ClinicalOutboxEventJpaEntity> eligible = outpatientMvpMode.enabled()
                ? outbox.lockUnpublishedForOutpatientMvp(100)
                : outbox.lockUnpublished(100);
        List<PendingPublication> pending = new ArrayList<>();
        for (ClinicalOutboxEventJpaEntity event : eligible) {
            if (event.getHeldAt() != null || event.getQuarantinedAt() != null) {
                continue;
            }
            if (event.getRetryCount() >= maxRetries) {
                quarantine(event);
                outbox.save(event);
                continue;
            }
            try {
                CorrelationData correlationData = new CorrelationData(event.getEventId().toString());
                send(event, correlationData);
                pending.add(new PendingPublication(event, correlationData));
            } catch (RuntimeException failure) {
                recordFailure(event, "send failed", failure);
                outbox.save(event);
            }
        }

        boolean interrupted = awaitConfirmations(pending);
        for (PendingPublication publication : pending) {
            ClinicalOutboxEventJpaEntity event = publication.event();
            if (interrupted && !publication.correlationData().getFuture().isDone()) {
                recordFailure(event, "publisher confirmation interrupted", null);
            } else {
                applyConfirmation(publication);
            }
            outbox.save(event);
        }
    }

    private void send(ClinicalOutboxEventJpaEntity event, CorrelationData correlationData) {
        byte[] payloadBytes = event.getPayloadBytes();
        if (payloadBytes == null) {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE, event.getEventType(), event.getPayload(), correlationData);
            return;
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, event.getEventType(),
                new Message(payloadBytes.clone(), properties), correlationData);
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
        if (event.getRetryCount() >= maxRetries) {
            quarantine(event);
        }
        if (failure == null) {
            log.warn("Clinical outbox publish failed eventId={} eventType={} retryCount={} reason={}",
                    event.getEventId(), event.getEventType(), event.getRetryCount(), reason);
        } else {
            log.warn("Clinical outbox publish failed eventId={} eventType={} retryCount={} reason={}",
                    event.getEventId(), event.getEventType(), event.getRetryCount(), reason, failure);
        }
    }

    private static void quarantine(ClinicalOutboxEventJpaEntity event) {
        if (event.getQuarantinedAt() == null) {
            event.setQuarantinedAt(Instant.now());
            event.setQuarantineReason(MAX_RETRIES_QUARANTINE_REASON);
        }
    }

    private record PendingPublication(
            ClinicalOutboxEventJpaEntity event,
            CorrelationData correlationData) {
    }
}
