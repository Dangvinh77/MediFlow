package com.mediflow.inpatient.infrastructure.messaging;

import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes committed outbox rows and records broker confirmations. */
@Component
@ConditionalOnProperty(name = "mediflow.inpatient.messaging.producer.enabled", havingValue = "true")
public class InpatientOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(InpatientOutboxDispatcher.class);
    private static final String EVENTS_EXCHANGE = "mediflow.events";
    private final InpatientOutboxPort outbox;
    private final RabbitTemplate rabbitTemplate;

    public InpatientOutboxDispatcher(InpatientOutboxPort outbox, RabbitTemplate rabbitTemplate) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        for (var event : outbox.claimPending(25)) {
            try {
                CorrelationData correlation = new CorrelationData(event.eventId().toString());
                rabbitTemplate.convertAndSend(EVENTS_EXCHANGE, event.eventType(), event.payloadJson(), correlation);
                var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new IllegalStateException(confirm.getReason() == null
                            ? "RabbitMQ did not confirm inpatient event" : confirm.getReason());
                }
                outbox.markPublished(event.eventId(), Instant.now());
            } catch (Exception exception) {
                outbox.markPublishFailed(event.eventId(), exception.getMessage());
                log.warn("Could not publish inpatient event {} ({})",
                        event.eventId(), event.eventType(), exception);
            }
        }
    }
}
