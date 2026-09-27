package com.mediflow.lab.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    public LabOutboxDispatcher(LabOutboxEventJpaRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${mediflow.events.outbox-poll-interval:1000}")
    @Transactional
    public void dispatchPending() {
        List<LabOutboxEventJpaEntity> events = repository.lockNextBatch();
        for (LabOutboxEventJpaEntity event : events) {
            try {
                publish(event);
                event.setPublishedAt(Instant.now());
            } catch (AmqpException exception) {
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
        rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, event.getEventType(),
                new Message(event.getPayload().toString().getBytes(StandardCharsets.UTF_8), properties));
    }
}
