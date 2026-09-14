package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the pharmacy outbox and delivers events with at-least-once semantics.
 *
 * <p>Rows remain pending when RabbitMQ is unavailable. Consumers must use eventId idempotency;
 * this is preferable to acknowledging a committed business change with a lost event.</p>
 */
@Component
@ConditionalOnProperty(
        name = "mediflow.pharmacy.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class PharmacyOutboxDispatcher {

    private static final String EXCHANGE = "mediflow.events";

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final PharmacyOutboxClaimService claimService;
    private final String owner;
    private final int batchSize;
    private final long confirmTimeoutMs;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final int maxAttempts;
    private final MeterRegistry meterRegistry;

    /** Creates a fully configured dispatcher with bounded retry backoff and metrics. */
    public PharmacyOutboxDispatcher(
            RabbitTemplate rabbitTemplate,
            Clock clock,
            @Value("${mediflow.pharmacy.outbox.batch-size:100}") int batchSize,
            @Value("${mediflow.pharmacy.outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs,
            PharmacyOutboxClaimService claimService,
            @Value("${mediflow.pharmacy.outbox.retry.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${mediflow.pharmacy.outbox.retry.max-backoff-ms:60000}") long maxBackoffMs,
            @Value("${mediflow.pharmacy.outbox.retry.max-attempts:5}") int maxAttempts,
            MeterRegistry meterRegistry) {
        if (batchSize <= 0 || confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException("Pharmacy outbox batch size and confirm timeout must be positive");
        }
        if (initialBackoffMs <= 0 || maxBackoffMs < initialBackoffMs || maxAttempts <= 0) {
            throw new IllegalArgumentException("Outbox backoff configuration is invalid");
        }
        if (claimService == null) {
            throw new IllegalArgumentException("Pharmacy outbox claim service is required");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.claimService = claimService;
        this.owner = java.util.UUID.randomUUID().toString();
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.maxAttempts = maxAttempts;
        this.meterRegistry = meterRegistry;
    }

    /** Attempts delivery of the oldest pending events on a fixed delay. */
    @Scheduled(fixedDelayString = "${mediflow.pharmacy.outbox.dispatch-delay-ms:1000}")
    public void dispatchPending() {
        List<PharmacyEventOutboxJpaEntity> events = claimService.claim(batchSize, owner);
        for (PharmacyEventOutboxJpaEntity event : events) {
            try {
                Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(event.getEventId().toString())
                        .build();
                awaitBrokerConfirmation(event, message);
                if (claimService.markPublished(event.getEventId(), owner, Instant.now(clock))) {
                    increment("mediflow.pharmacy.outbox.published");
                }
            } catch (RuntimeException exception) {
                boolean quarantined = event.getAttempts() + 1 >= maxAttempts;
                claimService.markFailure(event.getEventId(), owner, safeError(exception),
                        nextAvailableAt(event), maxAttempts);
                increment("mediflow.pharmacy.outbox.retry");
                log.warn("Không thể phát event outbox {} qua {}: {}",
                        event.getEventId(), event.getRoutingKey(), exception.getMessage());
                // Non-critical notifications must not block the completion event of the saga.
                // Critical events preserve order until they are quarantined after maxAttempts.
                if (!quarantined && !isNonBlocking(event)) {
                    break;
                }
            }
        }
    }

    private boolean isNonBlocking(PharmacyEventOutboxJpaEntity event) {
        return "stock.low".equals(event.getRoutingKey())
                || "stock.adjusted".equals(event.getRoutingKey());
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? "Unknown publish failure"
                : message.substring(0, Math.min(message.length(), 500));
    }

    private Instant nextAvailableAt(PharmacyEventOutboxJpaEntity event) {
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

    private void increment(String name) {
        if (meterRegistry != null) {
            meterRegistry.counter(name).increment();
        }
    }

    private void awaitBrokerConfirmation(PharmacyEventOutboxJpaEntity event, Message message) {
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        rabbitTemplate.send(EXCHANGE, event.getRoutingKey(), message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ nack: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned unroutable event: " + correlation.getReturned().getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ confirm", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ publisher confirm failed", exception);
        }
    }
}
