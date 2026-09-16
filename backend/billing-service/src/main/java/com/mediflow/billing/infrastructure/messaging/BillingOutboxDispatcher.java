package com.mediflow.billing.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.billing.infrastructure.config.RabbitConfig;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.BillingEventOutboxJpaEntity;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/** Delivers Billing outbox rows with broker confirms, bounded retry and per-invoice ordering. */
@Component
@ConditionalOnProperty(name = "mediflow.billing.outbox.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class BillingOutboxDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final BillingOutboxClaimService claimService;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();
    private final int batchSize;
    private final long confirmTimeoutMs;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final int maxAttempts;
    private final MeterRegistry meterRegistry;

    public BillingOutboxDispatcher(RabbitTemplate rabbitTemplate,
                                   BillingOutboxClaimService claimService,
                                   Clock clock,
                                   @Value("${mediflow.billing.outbox.batch-size:100}") int batchSize,
                                   @Value("${mediflow.billing.outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs,
                                   @Value("${mediflow.billing.outbox.retry.initial-backoff-ms:1000}") long initialBackoffMs,
                                   @Value("${mediflow.billing.outbox.retry.max-backoff-ms:60000}") long maxBackoffMs,
                                   @Value("${mediflow.billing.outbox.retry.max-attempts:5}") int maxAttempts,
                                   MeterRegistry meterRegistry) {
        if (batchSize <= 0 || confirmTimeoutMs <= 0 || initialBackoffMs <= 0
                || maxBackoffMs < initialBackoffMs || maxAttempts <= 0) {
            throw new IllegalArgumentException("Billing outbox configuration is invalid");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.claimService = claimService;
        this.clock = clock;
        this.batchSize = batchSize;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.maxAttempts = maxAttempts;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${mediflow.billing.outbox.dispatch-delay-ms:1000}",
            initialDelayString = "${mediflow.billing.outbox.dispatch-initial-delay-ms:1000}")
    public void dispatchPending() {
        List<BillingEventOutboxJpaEntity> events = claimService.claim(batchSize, owner);
        for (BillingEventOutboxJpaEntity event : events) {
            try {
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(event.getEventId().toString())
                        .build();
                awaitBrokerConfirmation(event, message);
                if (claimService.markPublished(event.getEventId(), owner, Instant.now(clock))) {
                    meterRegistry.counter("mediflow.billing.outbox.published").increment();
                }
            } catch (RuntimeException exception) {
                claimService.markFailure(event.getEventId(), owner, safeError(exception),
                        nextAvailableAt(event), maxAttempts);
                meterRegistry.counter("mediflow.billing.outbox.retry").increment();
                log.warn("Không thể phát billing outbox event {} qua {}: {}",
                        event.getEventId(), event.getRoutingKey(), exception.getMessage());
                break;
            }
        }
    }

    private void awaitBrokerConfirmation(BillingEventOutboxJpaEntity event, Message message) {
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        rabbitTemplate.send(RabbitConfig.EXCHANGE, event.getRoutingKey(), message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ nack: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned event: " + correlation.getReturned().getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing billing event", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Billing RabbitMQ publisher confirm failed", exception);
        }
    }

    private Instant nextAvailableAt(BillingEventOutboxJpaEntity event) {
        int exponent = Math.min(event.getAttempts(), 30);
        long multiplier = 1L << exponent;
        long delay;
        try {
            delay = Math.multiplyExact(initialBackoffMs, multiplier);
        } catch (ArithmeticException overflow) {
            delay = maxBackoffMs;
        }
        return Instant.now(clock).plusMillis(Math.min(delay, maxBackoffMs));
    }

    private static String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
