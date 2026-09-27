package com.mediflow.lab.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabOutboxEventJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabOutboxEventJpaRepository;

/** Relays committed outbox rows and retains failed rows for a later bounded batch retry. */
@Component
public class LabOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LabOutboxDispatcher.class);

    private final LabOutboxEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMs;

    public LabOutboxDispatcher(LabOutboxEventJpaRepository repository, RabbitTemplate rabbitTemplate,
                               @Value("${mediflow.events.outbox-confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        if (confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException("Lab outbox confirm timeout must be positive");
        }
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${mediflow.events.outbox-poll-interval:1000}")
    @Transactional
    public void dispatchPending() {
        List<LabOutboxEventJpaEntity> events = repository.lockNextBatch();
        for (LabOutboxEventJpaEntity event : events) {
            try {
                publish(event);
                event.setPublishedAt(Instant.now());
            } catch (RuntimeException exception) {
                event.setRetryCount(event.getRetryCount() + 1);
                log.warn("Lab outbox publish failed eventId={} eventType={} retryCount={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount());
            }
        }
    }

    private void publish(LabOutboxEventJpaEntity event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(event.getEventId().toString());
        properties.setCorrelationId(event.getCorrelationId());
        Message message = new Message(event.getPayload().toString().getBytes(StandardCharsets.UTF_8), properties);
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, event.getEventType(), message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ nack: " + confirm.getReason());
            }
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                throw new IllegalStateException("RabbitMQ returned Lab outbox event: " + returned.getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting Lab outbox confirmation", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Lab RabbitMQ publisher confirm failed", exception);
        }
    }
}
